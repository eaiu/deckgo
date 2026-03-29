#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/common.sh"

load_root_env
require_command node "缺少 node。当前项目前端需要 Node.js 运行时。"
require_command npm "缺少 npm。当前项目前端使用 npm workspace。"

cd "${REPO_ROOT}/frontend"

if [[ ! -d node_modules ]]; then
  printf '%s\n' "Installing frontend dependencies..."
  npm install
fi

exec npm run dev:web
