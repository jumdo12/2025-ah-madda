#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -lt 1 ]]; then
    echo "Usage: $0 <scenario-name>" >&2
    exit 1
fi

PERF_DIR=$(cd "$(dirname "$0")" && pwd)
SERVER_DIR=$(cd "${PERF_DIR}/.." && pwd)
SCENARIO_DIRECTORY="${PERF_DIR}/scenarios/$1"
SCENARIO_FILE="${SCENARIO_DIRECTORY}/scenario.sh"

if [[ ! -f "$SCENARIO_FILE" ]]; then
    echo "Unknown performance scenario: $1" >&2
    exit 1
fi

source "${PERF_DIR}/lib/environment.sh"
source "${PERF_DIR}/lib/health.sh"
source "${PERF_DIR}/lib/mysql.sh"
source "${PERF_DIR}/lib/redis.sh"
source "${PERF_DIR}/lib/jwt.sh"
source "${PERF_DIR}/lib/event.sh"
source "${PERF_DIR}/lib/k6.sh"
source "${PERF_DIR}/lib/report.sh"
source "${PERF_DIR}/lib/assertion.sh"
source "${PERF_DIR}/lib/scenario.sh"
source "$SCENARIO_FILE"

perf_validate_scenario_contract
scenario_configure

perf_require_commands curl docker jq k6 openssl
perf_initialize_run "$SCENARIO_NAME"

echo "[1/7] Starting performance environment"
perf_start_environment

echo "[2/7] Waiting for WAS health check"
perf_wait_for_http_health "$PERF_BASE_URL/actuator/health" "$PERF_HEALTH_TIMEOUT_SECONDS"

echo "[3/7] Checking scenario preconditions"
scenario_check_precondition

echo "[4/7] Preparing scenario data"
scenario_prepare

echo "[5/7] Executing workload"
set +e
scenario_execute
PERF_WORKLOAD_EXIT_CODE=$?
set -e

echo "[6/7] Waiting for scenario completion"
if scenario_await_completion; then
    PERF_SCENARIO_CONVERGED=1
else
    PERF_SCENARIO_CONVERGED=0
fi

echo "[7/7] Verifying scenario result"
perf_assertions_reset
scenario_verify

verification_exit_code=0
perf_assertions_finish || verification_exit_code=$?
perf_print_report

exit "$verification_exit_code"
