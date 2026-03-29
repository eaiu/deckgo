#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/common.sh"

load_root_env
require_command psql "缺少 psql。请先安装 PostgreSQL 客户端或 PostgreSQL 服务。"

db_name="${DECKGO_DB_NAME:-deckgo}"
admin_db="${DECKGO_ADMIN_DB:-postgres}"
db_user="${DECKGO_DB_USERNAME:-postgres}"

if [[ -n "${DECKGO_DB_PASSWORD:-}" ]]; then
  export PGPASSWORD="${DECKGO_DB_PASSWORD}"
fi

exists="$(psql -U "${db_user}" -d "${admin_db}" -tAc "SELECT 1 FROM pg_database WHERE datname = '${db_name}'")"
if [[ "${exists}" == "1" ]]; then
  printf "Database '%s' already exists.\n" "${db_name}"
  exit 0
fi

psql -U "${db_user}" -d "${admin_db}" -c "CREATE DATABASE ${db_name}"
printf "Database '%s' created.\n" "${db_name}"
