#!/usr/bin/env bash
# Builds the signed release APK for Juras.
# Runs `gradle :app:assembleRelease`, which signs the APK when keystore.properties
# is present (see README "Release build"). Assumes `java` is on PATH.
# Extra args are forwarded to Gradle:  ./build-signed-release.sh --info

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Pre-flight checks ────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
    echo "ERROR: 'java' was not found on PATH." >&2
    exit 1
fi
if [[ ! -f "$SCRIPT_DIR/keystore.properties" ]]; then
    echo "ERROR: keystore.properties not found. Release signing needs it (see README -> Release build)." >&2
    exit 1
fi

# ── Build ────────────────────────────────────────────────────────────────────
echo "Building signed release APK..."
./gradlew :app:assembleRelease "$@"

# ── Report ───────────────────────────────────────────────────────────────────
APK="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
if [[ -f "$APK" ]]; then
    SIZE_BYTES=$(wc -c < "$APK")
    SIZE_MB=$(awk "BEGIN {printf \"%.1f\", $SIZE_BYTES / 1048576}")
    echo ""
    echo "Done: $APK ($SIZE_MB MB)"
else
    echo "ERROR: Build succeeded but the APK was not found at $APK" >&2
    exit 1
fi
