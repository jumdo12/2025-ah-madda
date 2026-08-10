#!/usr/bin/env bash

scenario_configure() {
    SCENARIO_NAME="stream-e2e"
    SCENARIO_REQUEST_COUNT="${REQUEST_COUNT:-200}"
    SCENARIO_MEMBER_OFFSET="${MEMBER_OFFSET:-1}"
    SCENARIO_TOKEN_COUNT=$((SCENARIO_REQUEST_COUNT + SCENARIO_MEMBER_OFFSET))
    SCENARIO_TOKEN_FILE="${TOKEN_FILE:-/tmp/ahmadda-perf-access-tokens.csv}"
    SCENARIO_TIMEOUT_SECONDS="${MAX_WAIT_SECONDS:-90}"
    SCENARIO_WORKLOAD_FILE="${PERF_DIR}/k6/seat-contention-reservation-only.js"
    SCENARIO_STREAM_KEY="${STREAM_KEY:-event-participation:stream}"
    SCENARIO_DLQ_STREAM_KEY="${DLQ_STREAM_KEY:-event-participation:dlq}"
    SCENARIO_CONSUMER_GROUP="${CONSUMER_GROUP:-event-participation-db-writers}"
}

scenario_stream_is_drained() {
    local pending lag

    if ! pending=$(perf_stream_group_value \
        "$SCENARIO_STREAM_KEY" "$SCENARIO_CONSUMER_GROUP" pending); then
        return 1
    fi
    if ! lag=$(perf_stream_group_value \
        "$SCENARIO_STREAM_KEY" "$SCENARIO_CONSUMER_GROUP" lag); then
        return 1
    fi

    [[ "$pending" -eq 0 && "$lag" -eq 0 ]]
}

scenario_check_precondition() {
    perf_wait_until \
        "$SCENARIO_TIMEOUT_SECONDS" \
        1 \
        "Stream consumer group drain before test" \
        scenario_stream_is_drained
}

scenario_prepare() {
    perf_generate_tokens "$SCENARIO_TOKEN_COUNT" "$SCENARIO_TOKEN_FILE"
    SCENARIO_EVENT_ID=$(perf_create_event \
        "$SCENARIO_REQUEST_COUNT" "$SCENARIO_TOKEN_FILE")
    SCENARIO_DLQ_COUNT_BEFORE=$(perf_redis_command XLEN "$SCENARIO_DLQ_STREAM_KEY")

    echo "Created eventId=${SCENARIO_EVENT_ID}"
}

scenario_execute() {
    perf_run_k6 \
        "$SCENARIO_WORKLOAD_FILE" \
        "$PERF_K6_SUMMARY_FILE" \
        "BASE_URL=${PERF_BASE_URL}" \
        "EVENT_ID=${SCENARIO_EVENT_ID}" \
        "TOKEN_FILE=${SCENARIO_TOKEN_FILE}" \
        "RESERVATION_REQUEST_COUNT=${SCENARIO_REQUEST_COUNT}" \
        "RESERVATION_RATE_PER_SECOND=${SCENARIO_REQUEST_COUNT}" \
        "MEMBER_OFFSET=${SCENARIO_MEMBER_OFFSET}"
}

scenario_processing_completed() {
    local guest_count pending lag

    guest_count=$(perf_count_event_guests "$SCENARIO_EVENT_ID")
    pending=$(perf_stream_group_value \
        "$SCENARIO_STREAM_KEY" "$SCENARIO_CONSUMER_GROUP" pending)
    lag=$(perf_stream_group_value \
        "$SCENARIO_STREAM_KEY" "$SCENARIO_CONSUMER_GROUP" lag)

    [[ "$guest_count" -eq "$SCENARIO_REQUEST_COUNT" \
        && "$pending" -eq 0 \
        && "$lag" -eq 0 ]]
}

scenario_await_completion() {
    # 최대 5회 처리의 Backoff(5 + 10 + 20 + 40 = 75초)에
    # Pending 조회 주기와 처리 시간을 고려해 90초 동안 최종 수렴을 기다린다.
    perf_wait_until \
        "$SCENARIO_TIMEOUT_SECONDS" \
        1 \
        "DB persistence and Stream Pending/Lag convergence" \
        scenario_processing_completed
}

scenario_verify() {
    local success_count sold_out_count unexpected_count
    local guest_count participant_count remaining pending lag
    local dlq_count_after dlq_delta rps p95_ms

    perf_assert_equals "K6 exit code" "0" "$PERF_WORKLOAD_EXIT_CODE"
    perf_assert_equals "Converged in time" "1" "$PERF_SCENARIO_CONVERGED"

    if [[ ! -f "$PERF_K6_SUMMARY_FILE" ]]; then
        perf_assert_equals "K6 summary exists" "true" "false"
        return
    fi

    perf_assert_equals "K6 summary exists" "true" "true"

    success_count=$(perf_k6_metric \
        "$PERF_K6_SUMMARY_FILE" '.metrics.reservation_success.count')
    sold_out_count=$(perf_k6_metric \
        "$PERF_K6_SUMMARY_FILE" '.metrics.reservation_sold_out.count')
    unexpected_count=$(perf_k6_metric \
        "$PERF_K6_SUMMARY_FILE" '.metrics.reservation_unexpected.count')
    rps=$(perf_k6_metric "$PERF_K6_SUMMARY_FILE" '.metrics.http_reqs.rate')
    p95_ms=$(perf_k6_metric \
        "$PERF_K6_SUMMARY_FILE" '.metrics.http_req_duration["p(95)"]')

    guest_count=$(perf_count_event_guests "$SCENARIO_EVENT_ID")
    participant_count=$(perf_redis_command \
        SCARD "event:{${SCENARIO_EVENT_ID}}:seat-participants")
    remaining=$(perf_redis_command \
        HGET "event:{${SCENARIO_EVENT_ID}}:seat-inventory" remaining)
    pending=$(perf_stream_group_value \
        "$SCENARIO_STREAM_KEY" "$SCENARIO_CONSUMER_GROUP" pending)
    lag=$(perf_stream_group_value \
        "$SCENARIO_STREAM_KEY" "$SCENARIO_CONSUMER_GROUP" lag)
    dlq_count_after=$(perf_redis_command XLEN "$SCENARIO_DLQ_STREAM_KEY")
    dlq_delta=$((dlq_count_after - SCENARIO_DLQ_COUNT_BEFORE))

    perf_assert_equals "HTTP accepted" "$SCENARIO_REQUEST_COUNT" "$success_count"
    perf_assert_equals "HTTP sold out" "0" "$sold_out_count"
    perf_assert_equals "HTTP unexpected" "0" "$unexpected_count"
    perf_assert_equals "DB persisted" "$SCENARIO_REQUEST_COUNT" "$guest_count"
    perf_assert_equals "Redis participants" "$SCENARIO_REQUEST_COUNT" "$participant_count"
    perf_assert_equals "Redis remaining" "0" "$remaining"
    perf_assert_equals "Stream pending" "0" "$pending"
    perf_assert_equals "Stream lag" "0" "$lag"
    perf_assert_equals "DLQ delta" "0" "$dlq_delta"
    perf_record_metric "Throughput" "$(printf '%.1f RPS' "$rps")"
    perf_record_metric "HTTP p95" "$(printf '%.1f ms' "$p95_ms")"
}
