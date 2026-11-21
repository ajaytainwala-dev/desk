#!/usr/bin/env bash
# Deprecated unverified downloader.
# Use the pinned checksummed provisioner instead.
set -euo pipefail
echo "error: .github/scripts/fetch-jdtls.sh is disabled (unsafe: no pinned checksum)." >&2
echo "Use: bash .github/scripts/provision-jdtls.sh [install-root]" >&2
echo "Or:  Java API JdtLsProvisioner.ensureInstalled(...)" >&2
exit 2
