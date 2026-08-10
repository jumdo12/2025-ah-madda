#!/usr/bin/env bash

PERF_COMPOSE_FILE="${PERF_COMPOSE_FILE:-${SERVER_DIR}/compose.perf.yml}"
PERF_BUILD_IMAGES="${PERF_BUILD_IMAGES:-true}"
PERF_BASE_URL="${BASE_URL:-http://localhost:8080}"
PERF_HEALTH_TIMEOUT_SECONDS="${PERF_HEALTH_TIMEOUT_SECONDS:-120}"

perf_require_commands() {
    local command_name
    for command_name in "$@"; do
        if ! command -v "$command_name" >/dev/null 2>&1; then
            echo "Required command is missing: ${command_name}" >&2
            exit 1
        fi
    done
}

perf_start_environment() {
    if [[ "$PERF_BUILD_IMAGES" == "true" ]]; then
        docker compose -f "$PERF_COMPOSE_FILE" up -d --build
        return
    fi

    docker compose -f "$PERF_COMPOSE_FILE" up -d
}
