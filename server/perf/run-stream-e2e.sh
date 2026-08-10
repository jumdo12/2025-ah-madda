#!/usr/bin/env bash

set -euo pipefail

PERF_DIR=$(cd "$(dirname "$0")" && pwd)

exec "${PERF_DIR}/run.sh" stream-e2e "$@"
