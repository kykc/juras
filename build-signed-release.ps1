<#
.SYNOPSIS
    Builds the signed release APK for Juras.

.DESCRIPTION
    Runs `gradle :app:assembleRelease`, which signs the APK when keystore.properties
    is present (see README "Release build"). Assumes `java` and `gradle` are on PATH.
.EXAMPLE
    .\build-signed-release.ps1
    .\build-signed-release.ps1 --info     # extra args are forwarded to Gradle
#>

$ErrorActionPreference = 'Stop'

# Always run from the project root (this script's directory).
Set-Location -Path $PSScriptRoot

# ── Pre-flight checks ────────────────────────────────────────────────────────
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    Write-Error "'gradle' was not found on PATH."
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "'java' was not found on PATH."
}
if (-not (Test-Path -Path (Join-Path $PSScriptRoot 'keystore.properties'))) {
    Write-Error "keystore.properties not found. Release signing needs it (see README -> Release build)."
}

# ── Build ────────────────────────────────────────────────────────────────────
Write-Host "Building signed release APK..." -ForegroundColor Cyan
.\gradlew.bat :app:assembleRelease @args
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed (exit code $LASTEXITCODE)."
}

# ── Report ───────────────────────────────────────────────────────────────────
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (Test-Path -Path $apk) {
    $sizeMb = [math]::Round((Get-Item $apk).Length / 1MB, 1)
    Write-Host ""
    Write-Host "Done: $apk ($sizeMb MB)" -ForegroundColor Green
} else {
    Write-Error "Build succeeded but the APK was not found at $apk"
}
