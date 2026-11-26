#!/usr/bin/env bash
# Inside-out codesign for PyInstaller .app (never use --deep).
set -euo pipefail

APP_PATH="${1:?Usage: macos_codesign.sh path/to/AIQATestStudio.app}"
IDENTITY="${MACOS_CODESIGN_IDENTITY:?Set MACOS_CODESIGN_IDENTITY}"
ENTITLEMENTS="$(cd "$(dirname "$0")/.." && pwd)/packaging/entitlements.plist"

if [[ ! -d "${APP_PATH}" ]]; then
  echo "ERROR: app not found: ${APP_PATH}"
  exit 1
fi
if [[ ! -f "${ENTITLEMENTS}" ]]; then
  echo "ERROR: missing ${ENTITLEMENTS}"
  exit 1
fi

sign_file() {
  local target="$1"
  codesign --force --options runtime --timestamp \
    --entitlements "${ENTITLEMENTS}" \
    --sign "${IDENTITY}" \
    "${target}"
}

echo "=== Codesign inside-out: ${APP_PATH} ==="

# Regular files only (skip symlinks), deepest paths first
while IFS= read -r -d '' file; do
  if [[ -x "${file}" || "${file}" == *.dylib || "${file}" == *.so ]]; then
    # Skip non-Mach-O (scripts/text) without aborting the whole run
    if file "${file}" 2>/dev/null | grep -q 'Mach-O\|shared library\|executable'; then
      echo "sign file: ${file}"
      sign_file "${file}"
    fi
  fi
done < <(
  find "${APP_PATH}/Contents" -type f -print0 \
    | perl -0 -e 'print sort { ($b =~ tr|/||) <=> ($a =~ tr|/||) or length($b) <=> length($a) } <>'
)

# Framework bundles after nested binaries
while IFS= read -r -d '' fw; do
  echo "sign framework: ${fw}"
  sign_file "${fw}"
done < <(find "${APP_PATH}/Contents" -type d -name "*.framework" -print0 \
  | perl -0 -e 'print sort { ($b =~ tr|/||) <=> ($a =~ tr|/||) } <>')

echo "sign app bundle: ${APP_PATH}"
sign_file "${APP_PATH}"
codesign --verify --strict --verbose=2 "${APP_PATH}"
echo "=== Codesign complete ==="
