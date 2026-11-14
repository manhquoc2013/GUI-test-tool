#!/usr/bin/env bash
# Create DMG from AIQATestStudio.app (optional codesign/notarize when secrets present).
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

META_JSON="$(uv run python -m ai_qa_test_studio --print-metadata-json)"
VERSION="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["version"])')"
EXE_NAME="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["exe_name"])')"
ARCH="$(uname -m)"
case "${ARCH}" in
  x86_64) ARCH="x64" ;;
  arm64) ARCH="arm64" ;;
esac

APP="dist/${EXE_NAME}.app"
if [[ ! -d "${APP}" ]]; then
  echo "ERROR: missing ${APP} — run scripts/package_app.sh first"
  exit 1
fi

if [[ -n "${MACOS_CODESIGN_IDENTITY:-}" ]]; then
  bash scripts/macos_codesign.sh "${APP}"
fi

STAGE="dist/dmg-stage"
rm -rf "${STAGE}"
mkdir -p "${STAGE}"
cp -R "${APP}" "${STAGE}/"
ln -sf /Applications "${STAGE}/Applications"

DMG="dist/${EXE_NAME}-${VERSION}-macos-${ARCH}.dmg"
rm -f "${DMG}"
hdiutil create -volname "${EXE_NAME}" -srcfolder "${STAGE}" -ov -format UDZO "${DMG}"

if [[ -n "${MACOS_NOTARY_PROFILE:-}" ]]; then
  xcrun notarytool submit "${DMG}" --keychain-profile "${MACOS_NOTARY_PROFILE}" --wait
  xcrun stapler staple "${DMG}"
fi

echo "DMG: ${DMG}"
