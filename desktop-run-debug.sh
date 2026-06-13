#!/usr/bin/env bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "Building and running debug app bundle..."

${SCRIPT_DIR}/gradlew :desktopApp:run