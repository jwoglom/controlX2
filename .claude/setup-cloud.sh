#!/usr/bin/env bash
set -euo pipefail

# Backwards-compatible wrapper.
# Preferred script is .claude/cloud-setup.sh.
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/cloud-setup.sh" "$@"
