# Build Inno Setup installer from PyInstaller onedir (expects dist/AIQATestStudio already built/signed).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root
. (Join-Path $Root "scripts\sign_common.ps1")

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

$IsccArgs = @(
    "/DMyAppVersion=$($Meta.version)",
    "/DMyAppPublisher=$($Meta.publisher)",
    "/DMyAppURL=$($Meta.publisher_url)"
)

if ($env:WINDOWS_CERT_PFX) {
    # Delegate signing to our helper so the PFX path/password never reach the
    # compiler command line (they would leak into build logs otherwise).
    $SignHelper = Join-Path $Root "scripts\inno_sign.ps1"
    $SignCommand = "/Saiqa=powershell -NoProfile -ExecutionPolicy Bypass -File `"$SignHelper`" `$f"
    $IsccArgs += "/DSignedBuild"
    $IsccArgs += $SignCommand
    Write-Host "Inno sign tool: $SignCommand"
    # pwsh 7.3+ passes native arguments verbatim; pin it so quoting never drifts.
    if (Get-Variable -Name PSNativeCommandArgumentPassing -ErrorAction SilentlyContinue) {
        $PSNativeCommandArgumentPassing = "Standard"
    }
} else {
    Write-Warning "WINDOWS_CERT_PFX not set — Setup and uninstaller will be unsigned"
}

& $Iscc @IsccArgs "scripts\installer.iss"
if ($LASTEXITCODE -ne 0) { throw "ISCC failed" }

$Setup = Get-ChildItem dist -Filter "AIQATestStudio-*-Setup.exe" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $Setup) { throw "Setup.exe was not produced" }

if ($env:WINDOWS_CERT_PFX) {
    try {
        # Inno already signed it; re-verify by hash to catch a broken timestamp.
        Assert-AuthenticodeValid $Setup.FullName
    } finally {
        Remove-TempPfx
    }
}
Write-Host "Setup: $($Setup.FullName)"
