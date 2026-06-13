#!/usr/bin/env bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "Building unsigned release app bundle..."

${SCRIPT_DIR}/gradlew :desktopApp:createReleaseDistributable