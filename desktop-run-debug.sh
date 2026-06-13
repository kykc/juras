#!/usr/bin/env bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "Building and running debug app bundle..."
if [ -f /etc/NIXOS ]; then
    LD_LIBRARY_PATH=${NIX_LD_LIBRARY_PATH} ${SCRIPT_DIR}/gradlew :desktopApp:run
else
    ${SCRIPT_DIR}/gradlew :desktopApp:run
fi
