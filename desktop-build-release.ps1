<#
.SYNOPSIS
    Builds the release desktop app bundle for Juras.

.DESCRIPTION
    Runs `gradle :desktopApp:createReleaseDistributable`. Assumes `java` is on PATH.
.EXAMPLE
    .\desktop-build-release.ps1
    .\desktop-build-release.ps1 --info     # extra args are forwarded to Gradle
#>

$ErrorActionPreference = 'Stop'

# Always run from the project root (this script's directory).
Set-Location -Path $PSScriptRoot

# ── Pre-flight checks ────────────────────────────────────────────────────────
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "'java' was not found on PATH."
}

# ── Build ────────────────────────────────────────────────────────────────────
Write-Host "Building release app bundle..." -ForegroundColor Cyan
.\gradlew.bat :desktopApp:createReleaseDistributable @args
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed (exit code $LASTEXITCODE)."
}
