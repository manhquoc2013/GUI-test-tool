#!/usr/bin/env bash
# Write + sign SHA256SUMS for release artifacts in a directory.
# Signing uses the pinned OpenPGP release key (RELEASE_SIGNING_KEY) so the
# in-app updater can verify what it downloads.
set -euo pipefail

TARGET_DIR="${1:?Usage: make_checksums.sh dist_dir}"
cd "${TARGET_DIR}"
rm -f SHA256SUMS SHA256SUMS.asc
shopt -s nullglob
FILES=(*.zip *-Setup.exe *.dmg *.deb)
if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "No release artifacts in ${TARGET_DIR}"
  exit 1
fi
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "${FILES[@]}" > SHA256SUMS
else
  sha256sum "${FILES[@]}" > SHA256SUMS
fi

if [[ -n "${RELEASE_SIGNING_KEY:-}" ]]; then
  uv run python "$(cd "$(dirname "$0")/.." && pwd)/scripts/release_sign.py" "${TARGET_DIR}"
else
  echo "WARNING: RELEASE_SIGNING_KEY not set — SHA256SUMS is unsigned" >&2
fi
echo "Wrote ${TARGET_DIR}/SHA256SUMS"
