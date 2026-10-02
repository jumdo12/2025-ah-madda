#!/usr/bin/env bash

PERF_REDIS_CONTAINER="${REDIS_CONTAINER:-server-redis-1}"

perf_redis_command() {
    docker exec "$PERF_REDIS_CONTAINER" redis-cli --raw "$@"
}

perf_stream_group_value() {
    local stream_key="$1"
    local consumer_group="$2"
    local field="$3"
    local group_json

    if ! group_json=$(docker exec "$PERF_REDIS_CONTAINER" \
        redis-cli --json XINFO GROUPS "$stream_key" 2>/dev/null); then
        return 1
    fi

    jq -er --arg group "$consumer_group" --arg field "$field" \
        '.[] | select(.name == $group) | .[$field]' <<<"$group_json"
}
