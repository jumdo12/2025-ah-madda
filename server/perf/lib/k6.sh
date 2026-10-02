#!/usr/bin/env bash

perf_run_k6() {
    local workload_file="$1"
    local summary_file="$2"
    shift 2

    env "$@" k6 run --quiet --summary-export="$summary_file" "$workload_file"
}

perf_k6_metric() {
    local summary_file="$1"
    local jq_expression="$2"
    jq -r "${jq_expression} // 0" "$summary_file"
}
