#!/usr/bin/env bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "Building unsigned debug APK..."
${SCRIPT_DIR}/gradlew :app:assembleDebug 

echo "Pushing to the target device via ADB..."
adb install --user 0 ${SCRIPT_DIR}/app/build/outputs/apk/debug/app-debug.apk
