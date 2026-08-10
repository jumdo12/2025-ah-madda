#!/usr/bin/env bash

PERF_MYSQL_CONTAINER="${MYSQL_CONTAINER:-server-mysql-1}"
PERF_MYSQL_DATABASE="${MYSQL_DATABASE:-ahmadda}"
PERF_MYSQL_USER="${MYSQL_USER:-root}"
PERF_MYSQL_PASSWORD="${MYSQL_PASSWORD:-1234}"

perf_mysql_query() {
    docker exec "$PERF_MYSQL_CONTAINER" mysql \
        "-u${PERF_MYSQL_USER}" "-p${PERF_MYSQL_PASSWORD}" \
        "$PERF_MYSQL_DATABASE" -N -B -e "$1" 2>/dev/null
}

perf_count_event_guests() {
    local event_id="$1"
    perf_mysql_query \
        "SELECT COUNT(*) FROM guest WHERE event_id = ${event_id} AND deleted_at IS NULL"
}
