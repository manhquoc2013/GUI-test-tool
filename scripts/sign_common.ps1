# Shared Authenticode helpers (dot-source from signing scripts).
# WINDOWS_CERT_PFX accepts either a path to an existing .pfx OR its base64 body,
# so CI can keep the certificate in a secret without staging it as a file.

function Get-SignToolPath {
    if ($env:SIGNTOOL_PATH) { return $env:SIGNTOOL_PATH }
    return "signtool"
}

function Get-TimestampUrl {
    if ($env:SIGNTOOL_TIMESTAMP_URL) { return $env:SIGNTOOL_TIMESTAMP_URL }
    return "http://timestamp.digicert.com"
}

$script:ResolvedPfx = $null
$script:TempPfx = $null

function Resolve-PfxPath([string]$Value) {
    if ($script:ResolvedPfx) { return $script:ResolvedPfx }
    if (Test-Path -LiteralPath $Value) {
        $script:ResolvedPfx = (Resolve-Path -LiteralPath $Value).Path
        return $script:ResolvedPfx
    }
    # Secret bodies often arrive with newlines/whitespace from YAML folding.
    $compact = ($Value -replace '\s', '')
    try {
        $bytes = [Convert]::FromBase64String($compact)
    } catch {
        throw "WINDOWS_CERT_PFX is neither an existing file nor a base64 PKCS#12 payload"
    }
    $tempRoot = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [IO.Path]::GetTempPath() }
    $path = Join-Path $tempRoot ("aiqa-" + [IO.Path]::GetRandomFileName() + ".pfx")
    [IO.File]::WriteAllBytes($path, $bytes)
    Write-Host "decoded PFX to $path"
    $script:TempPfx = $path
    $script:ResolvedPfx = $path
    return $path
}

function Invoke-AuthenticodeSign([string]$Path) {
    $pfx = Resolve-PfxPath $env:WINDOWS_CERT_PFX
    $signtool = Get-SignToolPath
    $password = $env:WINDOWS_CERT_PASSWORD
    if ($password) {
        & $signtool sign /f $pfx /p $password /fd SHA256 /tr (Get-TimestampUrl) /td SHA256 $Path
    } else {
        & $signtool sign /f $pfx /fd SHA256 /tr (Get-TimestampUrl) /td SHA256 $Path
    }
    if ($LASTEXITCODE -ne 0) { throw "signtool failed for $Path" }
}

function Assert-AuthenticodeValid([string]$Path) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -ne "Valid") {
        throw "Authenticode verification failed for $Path : $($signature.Status)"
    }
}

function Remove-TempPfx {
    if ($script:TempPfx -and (Test-Path -LiteralPath $script:TempPfx)) {
        Remove-Item -LiteralPath $script:TempPfx -Force -ErrorAction SilentlyContinue
    }
}
