#!/usr/bin/env bash

set -euo pipefail

PERF_DIR=$(cd "$(dirname "$0")/.." && pwd)
SERVER_DIR=$(cd "${PERF_DIR}/.." && pwd)
PERF_BASE_URL="${BASE_URL:-http://localhost:8080}"

source "${PERF_DIR}/lib/mysql.sh"
source "${PERF_DIR}/lib/jwt.sh"
source "${PERF_DIR}/lib/event.sh"

EVENT_CAPACITY="${EVENT_CAPACITY:-200}"
TOKEN_FILE="${TOKEN_FILE:-/tmp/ahmadda-perf-access-tokens.csv}"

perf_create_event "$EVENT_CAPACITY" "$TOKEN_FILE"
