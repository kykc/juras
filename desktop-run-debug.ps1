<#
.SYNOPSIS
    Builds and runs the debug desktop app bundle for Juras.

.DESCRIPTION
    Runs `gradle :desktopApp:run`. Assumes `java` is on PATH.
.EXAMPLE
    .\desktop-run-debug.ps1
    .\desktop-run-debug.ps1 --info     # extra args are forwarded to Gradle
#>

$ErrorActionPreference = 'Stop'

# Always run from the project root (this script's directory).
Set-Location -Path $PSScriptRoot

# ── Pre-flight checks ────────────────────────────────────────────────────────
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "'java' was not found on PATH."
}

# ── Build & run ──────────────────────────────────────────────────────────────
Write-Host "Building and running debug app bundle..." -ForegroundColor Cyan
.\gradlew.bat :desktopApp:run @args
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle run failed (exit code $LASTEXITCODE)."
}
