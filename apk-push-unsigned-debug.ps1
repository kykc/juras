<#
.SYNOPSIS
    Builds the unsigned debug APK for Juras and installs it on the connected device.

.DESCRIPTION
    Runs `gradle :app:assembleDebug`, then installs the resulting APK on the target
    device via ADB. Assumes `java` and `adb` are on PATH.
.EXAMPLE
    .\apk-push-unsigned-debug.ps1
    .\apk-push-unsigned-debug.ps1 --info     # extra args are forwarded to Gradle
#>

$ErrorActionPreference = 'Stop'

# Always run from the project root (this script's directory).
Set-Location -Path $PSScriptRoot

# ── Pre-flight checks ────────────────────────────────────────────────────────
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "'java' was not found on PATH."
}
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "'adb' was not found on PATH."
}

# ── Build ────────────────────────────────────────────────────────────────────
Write-Host "Building unsigned debug APK..." -ForegroundColor Cyan
.\gradlew.bat :app:assembleDebug @args
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed (exit code $LASTEXITCODE)."
}

# ── Install ──────────────────────────────────────────────────────────────────
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -Path $apk)) {
    Write-Error "Build succeeded but the APK was not found at $apk"
}

Write-Host "Pushing to the target device via ADB..." -ForegroundColor Cyan
adb install --user 0 $apk
if ($LASTEXITCODE -ne 0) {
    Write-Error "ADB install failed (exit code $LASTEXITCODE)."
}
