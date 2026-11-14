# Build Inno Setup installer from PyInstaller onedir (expects dist/AIQATestStudio already built/signed).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$MetaJson = uv run python -m ai_qa_test_studio --print-metadata-json
$Meta = $MetaJson | ConvertFrom-Json
$Iscc = $env:ISCC_PATH
if (-not $Iscc) {
    $candidates = @(
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $Iscc = $c; break }
    }
}
if (-not $Iscc -or -not (Test-Path $Iscc)) {
    throw "ISCC.exe not found — install Inno Setup 6"
}

& $Iscc "/DMyAppVersion=$($Meta.version)" "/DMyAppPublisher=$($Meta.publisher)" "/DMyAppURL=$($Meta.publisher_url)" "scripts\installer.iss"
if ($LASTEXITCODE -ne 0) { throw "ISCC failed" }

$Setup = Get-ChildItem dist -Filter "AIQATestStudio-*-Setup.exe" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($Setup -and $env:WINDOWS_CERT_PFX) {
    & "$Root\scripts\windows_sign.ps1" -Onedir (Split-Path $Setup.FullName -Parent) 2>$null
    # Sign the setup exe explicitly
    $env:SIGN_TARGET = $Setup.FullName
    $Signtool = if ($env:SIGNTOOL_PATH) { $env:SIGNTOOL_PATH } else { "signtool" }
    if ($env:WINDOWS_CERT_PASSWORD) {
        & $Signtool sign /f $env:WINDOWS_CERT_PFX /p $env:WINDOWS_CERT_PASSWORD /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $Setup.FullName
    } else {
        & $Signtool sign /f $env:WINDOWS_CERT_PFX /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $Setup.FullName
    }
}
Write-Host "Setup: $($Setup.FullName)"
