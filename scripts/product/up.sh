#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

product_load_env
product_configure_java_home
product_ensure_state_dirs
product_require_lsof

product_prepare_infrastructure
product_build_backend
product_start_application_stack detached
product_print_stack_ready_message
