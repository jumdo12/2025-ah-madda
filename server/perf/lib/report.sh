#!/usr/bin/env bash

PERF_RESULT_ROOT="${RESULT_DIRECTORY:-/tmp/ahmadda-perf-results}"

perf_initialize_run() {
    local scenario_name="$1"
    local run_id

    run_id="$(date +%Y%m%d-%H%M%S)-$$"
    PERF_RUN_DIRECTORY="${PERF_RESULT_ROOT}/${scenario_name}/${run_id}"
    PERF_K6_SUMMARY_FILE="${PERF_RUN_DIRECTORY}/k6-summary.json"
    PERF_VERIFICATION_REPORT_FILE="${PERF_RUN_DIRECTORY}/verification.txt"

    mkdir -p "$PERF_RUN_DIRECTORY"
    : >"$PERF_VERIFICATION_REPORT_FILE"
}

perf_record_assertion() {
    local status="$1"
    local label="$2"
    local expected="$3"
    local actual="$4"

    printf '%-4s  %-24s expected=%s actual=%s\n' \
        "$status" "$label" "$expected" "$actual" \
        | tee -a "$PERF_VERIFICATION_REPORT_FILE"
}

perf_record_metric() {
    local label="$1"
    local value="$2"

    printf 'INFO  %-24s %s\n' "$label" "$value" \
        | tee -a "$PERF_VERIFICATION_REPORT_FILE"
}

perf_print_report() {
    echo
    if [[ "$PERF_ASSERTION_FAILURES" -eq 0 ]]; then
        echo "E2E verification passed."
    else
        echo "E2E verification failed with ${PERF_ASSERTION_FAILURES} mismatches." >&2
    fi
    echo "K6 summary: ${PERF_K6_SUMMARY_FILE}"
    echo "Verification report: ${PERF_VERIFICATION_REPORT_FILE}"
}
