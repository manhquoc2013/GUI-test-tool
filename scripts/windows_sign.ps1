# Authenticode-sign onedir executables (and optionally dll/pyd) before ZIP/Inno.
# WINDOWS_CERT_PFX accepts either a path to an existing .pfx OR its base64 body.
param(
    [Parameter(Mandatory = $true)][string]$Onedir,
    [switch]$IncludeLibraries
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sign_common.ps1")

if (-not $env:WINDOWS_CERT_PFX) {
    Write-Warning "WINDOWS_CERT_PFX not set — skipping Authenticode"
    exit 0
}
if (-not (Test-Path $Onedir)) {
    throw "Onedir not found: $Onedir"
}

try {
    # Resolve once so a base64 secret is decoded a single time for all binaries.
    $null = Resolve-PfxPath $env:WINDOWS_CERT_PFX

    Get-ChildItem -Path $Onedir -Filter *.exe -File | ForEach-Object {
        Write-Host "signtool: $($_.FullName)"
        Invoke-AuthenticodeSign $_.FullName
    }
    Get-ChildItem -Path (Join-Path $Onedir "_internal") -Filter *.exe -File -Recurse -ErrorAction SilentlyContinue |
        ForEach-Object {
            Write-Host "signtool: $($_.FullName)"
            Invoke-AuthenticodeSign $_.FullName
        }

    if ($IncludeLibraries) {
        Get-ChildItem -Path (Join-Path $Onedir "_internal") -Include *.dll,*.pyd -File -Recurse -ErrorAction SilentlyContinue |
            ForEach-Object {
                Write-Host "signtool: $($_.FullName)"
                Invoke-AuthenticodeSign $_.FullName
            }
    }

    # Fail when a signature exists but is not valid (expired cert, bad timestamp).
    Get-ChildItem -Path $Onedir -Filter *.exe -File | ForEach-Object {
        Assert-AuthenticodeValid $_.FullName
    }
    Write-Host "=== Windows Authenticode complete ==="
} finally {
    Remove-TempPfx
}
