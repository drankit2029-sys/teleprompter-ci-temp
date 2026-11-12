#!/usr/bin/env bash
# Run Android Lint and ktlint on the Termux build phone.
#
# The phone is the build machine; this script syncs the project there and runs
# both checks remotely so the PC does not run Gradle or ktlint. The default
# pass runs the API-compatibility lint checks plus ktlint; pass --full-lint for
# the complete Android Lint suite (slower, useful before a checkpoint).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

exec "$ROOT/scripts/build-on-phone.sh" --project "$ROOT" --check "$@"
