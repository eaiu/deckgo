#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

load_root_env() {
  local env_file="${REPO_ROOT}/.env"
  if [[ -f "${env_file}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${env_file}"
    set +a
  fi
}

require_command() {
  local command_name="$1"
  local hint_message="$2"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    printf '%s\n' "${hint_message}" >&2
    exit 1
  fi
}
