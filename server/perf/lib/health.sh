#!/usr/bin/env bash

perf_wait_for_http_health() {
    local health_url="$1"
    local timeout_seconds="$2"
    local deadline=$((SECONDS + timeout_seconds))

    until curl -fsS "$health_url" >/dev/null 2>&1; do
        if (( SECONDS >= deadline )); then
            echo "Health check did not succeed within ${timeout_seconds} seconds: ${health_url}" >&2
            return 1
        fi
        sleep 2
    done
}

perf_wait_until() {
    local timeout_seconds="$1"
    local interval_seconds="$2"
    local description="$3"
    shift 3

    local deadline=$((SECONDS + timeout_seconds))
    while (( SECONDS < deadline )); do
        if "$@"; then
            return 0
        fi
        sleep "$interval_seconds"
    done

    echo "Timed out after ${timeout_seconds} seconds: ${description}" >&2
    return 1
}
