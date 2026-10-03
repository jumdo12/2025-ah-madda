#!/usr/bin/env bash
set -euo pipefail

APP_URL="${APP_URL:-http://127.0.0.1:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-ahmadda-mysql}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ahmadda}"
MYSQL_USER="${MYSQL_USER:-ahmadda}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-1234}"
JWT_ACCESS_SECRET="${JWT_ACCESS_SECRET:-local-access-secret-key-for-portfolio-only}"
MEMBER_COUNT="${MEMBER_COUNT:-30}"
PREFIX="${PREFIX:-perf-hikari}"
SEND=false

if [[ "${1:-}" == "--send" ]]; then
  SEND=true
fi

if ! docker ps --format '{{.Names}}' | grep -qx "${MYSQL_CONTAINER}"; then
  echo "MySQL container '${MYSQL_CONTAINER}' is not running. Start server/mysql/docker-compose.yml first." >&2
  exit 1
fi

MYSQL=(
  docker exec -i "${MYSQL_CONTAINER}"
  mysql
  "-u${MYSQL_USER}"
  "-p${MYSQL_PASSWORD}"
  "${MYSQL_DATABASE}"
  --batch
  --skip-column-names
)

seed_sql="$(mktemp)"
trap 'rm -f "${seed_sql}"' EXIT

{
  cat <<SQL
START TRANSACTION;

SET @prefix = '${PREFIX}';
SET @org_name = CONCAT(@prefix, '-org');
SET @event_title = CONCAT(@prefix, '-event');
SET @organizer_email = CONCAT(@prefix, '-organizer@ahmadda.local');

SELECT @group_id := organization_group_id
FROM organization_group
WHERE name = '기타'
ORDER BY organization_group_id
LIMIT 1;

INSERT INTO organization_group (name, created_at, updated_at)
SELECT '기타', NOW(6), NOW(6)
WHERE @group_id IS NULL;

SET @group_id = COALESCE(@group_id, LAST_INSERT_ID());

SELECT @org_id := organization_id
FROM organization
WHERE name = @org_name
  AND deleted_at IS NULL
ORDER BY organization_id
LIMIT 1;

INSERT INTO organization (created_at, updated_at, description, image_url, name)
SELECT NOW(6), NOW(6), 'perf test org', 'https://example.com/org.png', @org_name
WHERE @org_id IS NULL;

SET @org_id = COALESCE(@org_id, LAST_INSERT_ID());

INSERT INTO member (created_at, updated_at, email, name, profile_image_url, deleted_at)
VALUES (NOW(6), NOW(6), @organizer_email, 'perf-organizer', 'https://example.com/profile.png', NULL)
ON DUPLICATE KEY UPDATE
    member_id = LAST_INSERT_ID(member_id),
    name = VALUES(name),
    profile_image_url = VALUES(profile_image_url),
    deleted_at = NULL;

SET @organizer_id = LAST_INSERT_ID();

SELECT @organizer_member_id := organization_member_id
FROM organization_member
WHERE member_id = @organizer_id
  AND organization_id = @org_id
  AND deleted_at IS NULL
ORDER BY organization_member_id
LIMIT 1;

INSERT INTO organization_member (
    created_at,
    updated_at,
    nickname,
    member_id,
    organization_id,
    role,
    deleted_at,
    organization_group_id
)
SELECT NOW(6), NOW(6), 'perf-owner', @organizer_id, @org_id, 'ADMIN', NULL, @group_id
WHERE @organizer_member_id IS NULL;

SET @organizer_member_id = COALESCE(@organizer_member_id, LAST_INSERT_ID());
SQL

  for idx in $(seq -w 1 "${MEMBER_COUNT}"); do
    nickname="perf${idx}"
    cat <<SQL
SET @member_email = '${PREFIX}-member-${idx}@ahmadda.local';

INSERT INTO member (created_at, updated_at, email, name, profile_image_url, deleted_at)
VALUES (NOW(6), NOW(6), @member_email, '${nickname}', 'https://example.com/profile.png', NULL)
ON DUPLICATE KEY UPDATE
    member_id = LAST_INSERT_ID(member_id),
    name = VALUES(name),
    profile_image_url = VALUES(profile_image_url),
    deleted_at = NULL;

SET @member_id = LAST_INSERT_ID();

SELECT @organization_member_id := organization_member_id
FROM organization_member
WHERE member_id = @member_id
  AND organization_id = @org_id
  AND deleted_at IS NULL
ORDER BY organization_member_id
LIMIT 1;

INSERT INTO organization_member (
    created_at,
    updated_at,
    nickname,
    member_id,
    organization_id,
    role,
    deleted_at,
    organization_group_id
)
SELECT NOW(6), NOW(6), '${nickname}', @member_id, @org_id, 'USER', NULL, @group_id
WHERE @organization_member_id IS NULL;
SQL
  done

  cat <<SQL
SELECT @event_id := event_id
FROM event
WHERE title = @event_title
  AND organization_id = @org_id
  AND deleted_at IS NULL
ORDER BY event_id
LIMIT 1;

INSERT INTO event (
    created_at,
    updated_at,
    description,
    event_end,
    event_start,
    registration_end,
    registration_start,
    max_capacity,
    place,
    title,
    organization_id,
    deleted_at,
    is_approval_required,
    registration_closing_reminder_minutes_before
)
SELECT
    NOW(6),
    NOW(6),
    'perf test event',
    DATE_ADD(NOW(6), INTERVAL 2 DAY) + INTERVAL 2 HOUR,
    DATE_ADD(NOW(6), INTERVAL 2 DAY),
    DATE_ADD(NOW(6), INTERVAL 1 DAY),
    NOW(6),
    100,
    'local',
    @event_title,
    @org_id,
    NULL,
    0,
    30
WHERE @event_id IS NULL;

SET @event_id = COALESCE(@event_id, LAST_INSERT_ID());

INSERT IGNORE INTO event_organizer (event_id, organization_member_id, created_at, updated_at, deleted_at)
VALUES (@event_id, @organizer_member_id, NOW(6), NOW(6), NULL);

COMMIT;
SQL
} > "${seed_sql}"

"${MYSQL[@]}" < "${seed_sql}" >/dev/null

EVENT_ID="$("${MYSQL[@]}" -e "SELECT event_id FROM event WHERE title = '${PREFIX}-event' AND deleted_at IS NULL ORDER BY event_id LIMIT 1;")"
ORGANIZER_ID="$("${MYSQL[@]}" -e "SELECT m.member_id FROM member m WHERE m.email = '${PREFIX}-organizer@ahmadda.local' AND m.deleted_at IS NULL LIMIT 1;")"
MEMBER_IDS_CSV="$("${MYSQL[@]}" -e "
SELECT GROUP_CONCAT(organization_member_id ORDER BY organization_member_id SEPARATOR ',')
FROM (
    SELECT om.organization_member_id
    FROM organization_member om
    JOIN member m ON m.member_id = om.member_id
    JOIN organization o ON o.organization_id = om.organization_id
    WHERE m.email LIKE '${PREFIX}-member-%@ahmadda.local'
      AND m.deleted_at IS NULL
      AND om.deleted_at IS NULL
      AND o.name = '${PREFIX}-org'
      AND o.deleted_at IS NULL
    ORDER BY om.organization_member_id
    LIMIT ${MEMBER_COUNT}
) selected_members;
")"

if [[ -z "${EVENT_ID}" || -z "${ORGANIZER_ID}" || -z "${MEMBER_IDS_CSV}" ]]; then
  echo "Failed to prepare dummy event/member data." >&2
  exit 1
fi

TOKEN="$(
  ORGANIZER_ID="${ORGANIZER_ID}" JWT_ACCESS_SECRET="${JWT_ACCESS_SECRET}" python3 - <<'PY'
import base64
import hashlib
import hmac
import json
import os
import time

def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")

now = int(time.time())
header = {"alg": "HS256", "typ": "JWT"}
payload = {
    "memberId": int(os.environ["ORGANIZER_ID"]),
    "iat": now,
    "exp": now + 3600,
}

signing_input = ".".join(
    b64url(json.dumps(part, separators=(",", ":")).encode("utf-8"))
    for part in (header, payload)
).encode("ascii")
signature = hmac.new(
    os.environ["JWT_ACCESS_SECRET"].encode("utf-8"),
    signing_input,
    hashlib.sha256,
).digest()

print(f"{signing_input.decode('ascii')}.{b64url(signature)}")
PY
)"

REQUEST_BODY="{\"organizationMemberIds\":[${MEMBER_IDS_CSV}],\"content\":\"성능테스트\"}"

echo "Prepared ${MEMBER_COUNT} recipients for event ${EVENT_ID}."
echo "Grafana: http://localhost:3000/d/ahmadda-hikaricp/ahmadda-hikaricp"

if [[ "${SEND}" != true ]]; then
  echo "Dry run only. Re-run with --send to call the API."
  echo "APP_URL=${APP_URL} MYSQL_CONTAINER=${MYSQL_CONTAINER} bash server/perf/notify-30-members.sh --send"
  exit 0
fi

curl -i \
  -X POST "${APP_URL}/api/events/${EVENT_ID}/notify-organization-members" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${REQUEST_BODY}"
