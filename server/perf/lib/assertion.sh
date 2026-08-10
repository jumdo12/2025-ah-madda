#!/usr/bin/env bash

PERF_ASSERTION_FAILURES=0

perf_assertions_reset() {
    PERF_ASSERTION_FAILURES=0
}

perf_assert_equals() {
    local label="$1"
    local expected="$2"
    local actual="$3"

    if [[ "$actual" == "$expected" ]]; then
        perf_record_assertion PASS "$label" "$expected" "$actual"
        return
    fi

    perf_record_assertion FAIL "$label" "$expected" "$actual"
    PERF_ASSERTION_FAILURES=$((PERF_ASSERTION_FAILURES + 1))
}

perf_assertions_finish() {
    [[ "$PERF_ASSERTION_FAILURES" -eq 0 ]]
}
