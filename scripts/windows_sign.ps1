# Authenticode-sign onedir executables (and optionally dll/pyd) before ZIP/Inno.
param(
    [Parameter(Mandatory = $true)][string]$Onedir,
    [switch]$IncludeLibraries
)

$ErrorActionPreference = "Stop"
if (-not $env:WINDOWS_CERT_PFX) {
    Write-Warning "WINDOWS_CERT_PFX not set — skipping Authenticode"
    exit 0
}
if (-not (Test-Path $Onedir)) {
    throw "Onedir not found: $Onedir"
}

$Pfx = $env:WINDOWS_CERT_PFX
$Password = $env:WINDOWS_CERT_PASSWORD
$Signtool = $env:SIGNTOOL_PATH
if (-not $Signtool) {
    $Signtool = "signtool"
}

function Sign-One([string]$Path) {
    Write-Host "signtool: $Path"
    if ($Password) {
        & $Signtool sign /f $Pfx /p $Password /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $Path
    } else {
        & $Signtool sign /f $Pfx /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $Path
    }
    if ($LASTEXITCODE -ne 0) { throw "signtool failed for $Path" }
}

Get-ChildItem -Path $Onedir -Filter *.exe -File | ForEach-Object { Sign-One $_.FullName }
Get-ChildItem -Path (Join-Path $Onedir "_internal") -Filter *.exe -File -Recurse -ErrorAction SilentlyContinue |
    ForEach-Object { Sign-One $_.FullName }

if ($IncludeLibraries) {
    Get-ChildItem -Path (Join-Path $Onedir "_internal") -Include *.dll,*.pyd -File -Recurse -ErrorAction SilentlyContinue |
        ForEach-Object { Sign-One $_.FullName }
}

Write-Host "=== Windows Authenticode complete ==="
