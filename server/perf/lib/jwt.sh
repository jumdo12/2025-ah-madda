#!/usr/bin/env bash

PERF_JWT_SECRET="${JWT_SECRET:-local-access-secret-key-for-portfolio-only}"
PERF_TOKEN_TTL_SECONDS="${TOKEN_TTL_SECONDS:-31536000}"
PERF_ORGANIZATION_ID="${ORGANIZATION_ID:-1}"

perf_base64_url_encode() {
    openssl base64 -A | tr '+/' '-_' | tr -d '='
}

perf_create_access_token() {
    local member_id="$1"
    local issued_at="$2"
    local expires_at="$3"
    local header payload unsigned signature

    header=$(printf '%s' '{"alg":"HS256"}' | perf_base64_url_encode)
    payload=$(printf '{"memberId":%s,"iat":%s,"exp":%s}' \
        "$member_id" "$issued_at" "$expires_at" | perf_base64_url_encode)
    unsigned="${header}.${payload}"
    signature=$(printf '%s' "$unsigned" \
        | openssl dgst -sha256 -mac HMAC -macopt "key:${PERF_JWT_SECRET}" -binary \
        | perf_base64_url_encode)

    printf '%s.%s' "$unsigned" "$signature"
}

perf_generate_tokens() {
    local token_count="$1"
    local token_file="$2"
    local member_rows actual_count issued_at expires_at member_id email token

    member_rows=$(perf_mysql_query "
        SELECT member.member_id, member.email
        FROM organization_member
        JOIN member ON member.member_id = organization_member.member_id
        WHERE organization_member.organization_id = ${PERF_ORGANIZATION_ID}
          AND organization_member.deleted_at IS NULL
          AND member.deleted_at IS NULL
        ORDER BY (organization_member.role = 'ADMIN') DESC,
                 organization_member.organization_member_id
        LIMIT ${token_count}
    ")

    actual_count=$(printf '%s\n' "$member_rows" | awk 'NF { count++ } END { print count + 0 }')
    if [[ "$actual_count" -ne "$token_count" ]]; then
        echo "Expected ${token_count} organization members, but found ${actual_count}." >&2
        return 1
    fi

    mkdir -p "$(dirname "$token_file")"
    printf 'memberId,email,accessToken\n' >"$token_file"

    issued_at=$(date +%s)
    expires_at=$((issued_at + PERF_TOKEN_TTL_SECONDS))

    while IFS=$'\t' read -r member_id email; do
        token=$(perf_create_access_token "$member_id" "$issued_at" "$expires_at")
        printf '%s,%s,%s\n' "$member_id" "$email" "$token" >>"$token_file"
    done <<<"$member_rows"

    echo "Generated ${actual_count} access tokens: ${token_file}" >&2
}
