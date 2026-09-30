#!/usr/bin/env bash
# Point git at the checked-in hooks in .githooks (pre-commit: ktfmt + detekt on staged Kotlin).
set -euo pipefail
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "Git hooks installed: core.hooksPath=.githooks"
