# Sign one file on behalf of Inno Setup (used through the SignTool directive).
# Inno substitutes $f with the quoted target, so keep the command free of secrets:
# the certificate path/password come from the environment, not the command line.
param(
    [Parameter(Mandatory = $true)][string]$Path
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sign_common.ps1")

if (-not $env:WINDOWS_CERT_PFX) {
    Write-Warning "WINDOWS_CERT_PFX not set — Inno Setup signing skipped"
    exit 0
}

try {
    Write-Host "inno sign: $Path"
    Invoke-AuthenticodeSign $Path
    Assert-AuthenticodeValid $Path
} finally {
    Remove-TempPfx
}
