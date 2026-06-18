#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "${ROOT_DIR}/.env"
  set +a
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-jiber}"
DB_USER="${DB_USER:-jiber}"
DB_PASSWORD="${DB_PASSWORD:-}"

mysql_scalar_host() {
  local sql="$1"

  MYSQL_PWD="${DB_PASSWORD}" mysql \
    --protocol=TCP \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USER}" \
    --database="${DB_NAME}" \
    --connect-timeout=5 \
    --batch \
    --skip-column-names \
    --execute="${sql}" 2>/dev/null
}

mysql_scalar_compose() {
  local sql="$1"

  docker compose --project-directory "${ROOT_DIR}" exec -T mysql sh -c \
    'MYSQL_PWD="${MYSQL_PASSWORD:-}" mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user="${MYSQL_USER:-jiber}" --database="${MYSQL_DATABASE:-jiber}" --connect-timeout=5 --batch --skip-column-names --execute="$1"' \
    sh "${sql}" 2>/dev/null
}

mysql_scalar_container() {
  local sql="$1"

  docker exec jiber-mysql sh -c \
    'MYSQL_PWD="${MYSQL_PASSWORD:-}" mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user="${MYSQL_USER:-jiber}" --database="${MYSQL_DATABASE:-jiber}" --connect-timeout=5 --batch --skip-column-names --execute="$1"' \
    sh "${sql}" 2>/dev/null
}

mysql_scalar() {
  local sql="$1"
  local output

  if command -v mysql >/dev/null 2>&1; then
    if output="$(mysql_scalar_host "${sql}")"; then
      printf '%s\n' "${output}"
      return 0
    fi
  fi

  if command -v docker >/dev/null 2>&1; then
    if output="$(mysql_scalar_compose "${sql}")"; then
      printf '%s\n' "${output}"
      return 0
    fi
    if output="$(mysql_scalar_container "${sql}")"; then
      printf '%s\n' "${output}"
      return 0
    fi
  fi

  return 1
}

missing=()

check_count() {
  local label="$1"
  local sql="$2"
  local count

  if ! count="$(mysql_scalar "${sql}")"; then
    echo "[auth-schema] ERROR: unable to query database. Verify DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, or the running docker compose mysql service; values are not printed." >&2
    exit 2
  fi

  if [[ "${count}" == "1" ]]; then
    echo "[auth-schema] OK: ${label}"
  else
    missing+=("${label}")
  fi
}

check_count "users.password_hash" "
SELECT COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'password_hash';
"

check_count "users.email unique index (uk_users_email)" "
SELECT COUNT(*)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND INDEX_NAME = 'uk_users_email'
  AND COLUMN_NAME = 'email'
  AND NON_UNIQUE = 0;
"

check_count "user_social_accounts" "
SELECT COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_social_accounts';
"

check_count "oauth_pending_social_sessions" "
SELECT COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'oauth_pending_social_sessions';
"

if (( ${#missing[@]} > 0 )); then
  echo "[auth-schema] FAIL: latest auth schema preflight failed; db/004_auth_account_social_link.sql appears unapplied or partially applied." >&2
  for item in "${missing[@]}"; do
    echo "[auth-schema] missing: ${item}" >&2
  done
  echo "[auth-schema] Stop auth UX smoke, take a backup/snapshot, resolve duplicate legacy emails if needed, then apply the 004 migration once." >&2
  exit 1
fi

echo "[auth-schema] PASS: latest auth schema preflight passed. 004 migration prerequisites are present."
