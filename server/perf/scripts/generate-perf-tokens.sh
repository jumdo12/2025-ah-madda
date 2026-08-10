#!/usr/bin/env bash

set -euo pipefail

PERF_DIR=$(cd "$(dirname "$0")/.." && pwd)
SERVER_DIR=$(cd "${PERF_DIR}/.." && pwd)

source "${PERF_DIR}/lib/mysql.sh"
source "${PERF_DIR}/lib/jwt.sh"

TOKEN_COUNT="${TOKEN_COUNT:-201}"
TOKEN_FILE="${TOKEN_FILE:-/tmp/ahmadda-perf-access-tokens.csv}"

perf_generate_tokens "$TOKEN_COUNT" "$TOKEN_FILE"
