#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/common.sh"

load_root_env
require_command java "缺少 java。请先安装 JDK 17，再启动后端。"

cd "${REPO_ROOT}/backend"

exec ./mvnw -s .mvn/local-settings.xml spring-boot:run "$@"
