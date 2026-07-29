#!/usr/bin/env bash
# =============================================================================
#  run-seed.sh - loads the test dataset into the four service databases.
# =============================================================================
#  These scripts are NOT placed in /docker-entrypoint-initdb.d on purpose:
#  initdb runs before any service has started, so the tables created by Flyway
#  would not exist yet. Run this only after the services are up and healthy.
#
#  Usage:
#     ./run-seed.sh                      # against localhost:5432
#     PGHOST=db PGPORT=5432 ./run-seed.sh
#
#  The equivalent seeding also happens automatically inside each service at
#  startup when APP_SEEDER_ENABLED=true (see the *DataSeeder classes).
#  This script exists for the case where you want to reset the data without
#  restarting the containers.
# =============================================================================
set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
export PGHOST PGPORT

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

seed() {
  local db="$1" user="$2" password="$3" file="$4"
  echo ""
  echo "=== seeding ${db} as ${user} ============================================"
  PGPASSWORD="${password}" psql \
      --host="${PGHOST}" --port="${PGPORT}" \
      --username="${user}" --dbname="${db}" \
      --set=ON_ERROR_STOP=1 \
      --file="${SCRIPT_DIR}/${file}"
}

seed authdb    auth_service    "${AUTH_DB_PASSWORD:-auth_dev_pwd}"       01-seed-authdb.sql
seed profiledb profile_service "${PROFILE_DB_PASSWORD:-profile_dev_pwd}" 02-seed-profiledb.sql
seed postdb    post_service    "${POST_DB_PASSWORD:-post_dev_pwd}"       03-seed-postdb.sql
seed likedb    like_service    "${LIKE_DB_PASSWORD:-like_dev_pwd}"       04-seed-likedb.sql

echo ""
echo "Seed complete. Every account uses the password: Password123!"
