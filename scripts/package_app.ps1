# Build PyInstaller onedir + portable ZIP (Windows).
# Sign (if WINDOWS_CERT_PFX set) BEFORE zipping — Authenticode must be inside the ZIP.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

uv sync --extra build
$MetaJson = uv run python -m ai_qa_test_studio --print-metadata-json
$Meta = $MetaJson | ConvertFrom-Json
$Version = $Meta.version
$ExeName = $Meta.exe_name
$Arch = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "arm64" } else { "x64" }

Write-Host "=== Packaging $ExeName v$Version (windows-$Arch) ==="

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue build, "dist\$ExeName"
uv run pyinstaller --noconfirm packaging/AIQATestStudio.spec

$Onedir = Join-Path "dist" $ExeName
if (-not (Test-Path $Onedir)) {
    throw "Missing onedir $Onedir"
}

if ($env:WINDOWS_CERT_PFX) {
    & "$Root\scripts\windows_sign.ps1" -Onedir $Onedir -IncludeLibraries
} else {
    Write-Warning "WINDOWS_CERT_PFX not set — portable ZIP will be unsigned"
}

$ZipName = "$ExeName-$Version-windows-$Arch.zip"
$ZipPath = Join-Path "dist" $ZipName
if (Test-Path $ZipPath) { Remove-Item -Force $ZipPath }
Compress-Archive -Path $Onedir -DestinationPath $ZipPath
$MetaJson | Set-Content -Path (Join-Path "dist" "metadata.json") -Encoding UTF8
Write-Host "Portable zip: $ZipPath"
Write-Host "=== Package complete ==="
