#!/usr/bin/env bash
# Push every release-signing secret to GitHub in one shot.
#
# Requires an authenticated gh CLI (run `gh auth login` once). Secret values are
# streamed over stdin and never echoed or written to disk in base64 form.
#
# Usage:
#   bash scripts/setup_github_secrets.sh                 # only the keys present locally
#   WINDOWS_CERT_PFX_FILE=cert.pfx WINDOWS_CERT_PASSWORD=... \
#   MACOS_CERT_P12_FILE=devid.p12 MACOS_CERT_PASSWORD=... \
#     bash scripts/setup_github_secrets.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="${GITHUB_REPO:-manhquoc2013/GUI-test-tool}"
RELEASE_KEY="${RELEASE_SIGNING_KEY_FILE:-${ROOT}/packaging/release-signing-key.asc}"

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: gh CLI not installed — https://cli.github.com" >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "ERROR: gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

set_from_file() {
  local name="$1" path="$2"
  gh secret set "${name}" --repo "${REPO}" < "${path}"
  echo "  ok  ${name}"
}

set_from_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "  --  ${name} (not provided, skipped)"
    return 0
  fi
  printf '%s' "${!name}" | gh secret set "${name}" --repo "${REPO}"
  echo "  ok  ${name}"
}

# Base64-encode a binary certificate into a secret without leaking it to argv.
set_p12_from_file() {
  local name="$1" path="$2"
  base64 < "${path}" | gh secret set "${name}" --repo "${REPO}"
  echo "  ok  ${name} (base64 from ${path})"
}

echo "Repository: ${REPO}"

echo "[1/3] OpenPGP manifest signing (required — publish fails without it)"
if [[ -f "${RELEASE_KEY}" ]]; then
  set_from_file RELEASE_SIGNING_KEY "${RELEASE_KEY}"
else
  echo "ERROR: missing ${RELEASE_KEY}" >&2
  echo "Generate with: gpg --quick-gen-key ... && gpg --armor --export-secret-keys <id> > ${RELEASE_KEY}" >&2
  exit 1
fi

echo "[2/3] Windows Authenticode (optional — build stays unsigned without it)"
if [[ -n "${WINDOWS_CERT_PFX_FILE:-}" ]]; then
  set_p12_from_file WINDOWS_CERT_PFX "${WINDOWS_CERT_PFX_FILE}"
  set_from_env WINDOWS_CERT_PASSWORD
else
  echo "  --  WINDOWS_CERT_PFX (set WINDOWS_CERT_PFX_FILE=/path/to/cert.pfx)"
fi

echo "[3/3] macOS Developer ID + notarization (optional — build stays unsigned without it)"
if [[ -n "${MACOS_CERT_P12_FILE:-}" ]]; then
  set_p12_from_file MACOS_CERT_P12 "${MACOS_CERT_P12_FILE}"
  set_from_env MACOS_CERT_PASSWORD
else
  echo "  --  MACOS_CERT_P12 (set MACOS_CERT_P12_FILE=/path/to/devid.p12)"
fi
set_from_env MACOS_CODESIGN_IDENTITY
set_from_env MACOS_NOTARY_APPLE_ID
set_from_env MACOS_NOTARY_TEAM_ID
set_from_env MACOS_NOTARY_PASSWORD

echo
echo "Done. Verify with: gh secret list --repo ${REPO}"
