#!/usr/bin/env bash

perf_create_event() {
    local capacity="$1"
    local token_file="$2"
    local organizer_token registration_end event_start event_end
    local request_body response_file http_status event_id

    if [[ ! -f "$token_file" ]]; then
        echo "Token file does not exist: ${token_file}" >&2
        return 1
    fi

    organizer_token=$(awk -F, 'NR == 2 { print $3 }' "$token_file")
    if [[ -z "$organizer_token" ]]; then
        echo "Organizer token was not found in ${token_file}." >&2
        return 1
    fi

    IFS=$'\t' read -r registration_end event_start event_end < <(
        perf_mysql_query "
            SELECT DATE_FORMAT(NOW() + INTERVAL 1 DAY, '%Y-%m-%dT%H:%i:%s'),
                   DATE_FORMAT(NOW() + INTERVAL 2 DAY, '%Y-%m-%dT%H:%i:%s'),
                   DATE_FORMAT(NOW() + INTERVAL 3 DAY, '%Y-%m-%dT%H:%i:%s')
        "
    )

    request_body=$(jq -n \
        --arg title "PERF EVENT $(date +%s)" \
        --arg registrationEnd "$registration_end" \
        --arg eventStart "$event_start" \
        --arg eventEnd "$event_end" \
        --argjson maxCapacity "$capacity" \
        '{
            title: $title,
            description: "Automated performance scenario",
            place: "performance-test",
            registrationEnd: $registrationEnd,
            eventStart: $eventStart,
            eventEnd: $eventEnd,
            maxCapacity: $maxCapacity,
            questions: [],
            eventOrganizerIds: [],
            groupIds: [],
            isApprovalRequired: false
        }')

    response_file=$(mktemp)
    http_status=$(curl -sS -o "$response_file" -w '%{http_code}' \
        -X POST "${PERF_BASE_URL}/api/organizations/${PERF_ORGANIZATION_ID}/events" \
        -H "Authorization: Bearer ${organizer_token}" \
        -H 'Content-Type: application/json' \
        --data "$request_body")

    if [[ "$http_status" != "201" ]]; then
        echo "Event creation failed with HTTP ${http_status}." >&2
        cat "$response_file" >&2
        rm -f "$response_file"
        return 1
    fi

    event_id=$(jq -r '.eventId // empty' "$response_file")
    rm -f "$response_file"

    if [[ -z "$event_id" ]]; then
        echo "eventId was not found in the event creation response." >&2
        return 1
    fi

    echo "$event_id"
}
