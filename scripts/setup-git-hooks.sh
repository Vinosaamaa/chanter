#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

chmod +x .githooks/pre-push
git config core.hooksPath .githooks

echo "Installed git hooks from .githooks/"
echo "Direct pushes to main are blocked locally."
