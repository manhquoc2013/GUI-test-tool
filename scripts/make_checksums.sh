#!/usr/bin/env bash
# Write SHA256SUMS for release artifacts in a directory.
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

if [[ -n "${GPG_RELEASE_KEY:-}" ]] && command -v gpg >/dev/null 2>&1; then
  gpg --batch --yes --detach-sign --armor -u "${GPG_RELEASE_KEY}" -o SHA256SUMS.asc SHA256SUMS
fi
echo "Wrote ${TARGET_DIR}/SHA256SUMS"
