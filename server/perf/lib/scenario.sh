#!/usr/bin/env bash

perf_validate_scenario_contract() {
    local required_functions=(
        scenario_configure
        scenario_check_precondition
        scenario_prepare
        scenario_execute
        scenario_await_completion
        scenario_verify
    )
    local function_name

    for function_name in "${required_functions[@]}"; do
        if ! declare -F "$function_name" >/dev/null; then
            echo "Scenario function is missing: ${function_name}" >&2
            exit 1
        fi
    done
}
