#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Optional local credentials; CI can supply the same variables as secrets.
if [[ -f .signing/release.env ]]; then
    set -a
    source .signing/release.env
    set +a
fi
: "${CAREWINDOW_KEYSTORE:?Set CAREWINDOW_KEYSTORE or create .signing/release.env}"
: "${CAREWINDOW_STORE_PASSWORD:?Set CAREWINDOW_STORE_PASSWORD}"
: "${CAREWINDOW_KEY_ALIAS:?Set CAREWINDOW_KEY_ALIAS}"
: "${CAREWINDOW_KEY_PASSWORD:?Set CAREWINDOW_KEY_PASSWORD}"
exec ./gradlew :core:test :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease --console=plain "$@"
