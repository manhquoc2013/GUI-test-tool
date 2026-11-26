#!/usr/bin/env bash
# Create DMG from AIQATestStudio.app (codesign + notarize when credentials exist).
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

if [[ -n "${MACOS_CODESIGN_IDENTITY:-}" ]]; then
  # The disk image carries its own signature so `spctl` accepts it offline.
  codesign --force --timestamp --sign "${MACOS_CODESIGN_IDENTITY}" "${DMG}"
  codesign --verify --verbose=2 "${DMG}"
fi

notarize() {
  # Prefer explicit App Store Connect credentials: `--keychain-profile` needs a
  # profile created by `notarytool store-credentials`, which fresh runners lack.
  if [[ -n "${MACOS_NOTARY_APPLE_ID:-}" && -n "${MACOS_NOTARY_TEAM_ID:-}" && -n "${MACOS_NOTARY_PASSWORD:-}" ]]; then
    xcrun notarytool submit "${DMG}" \
      --apple-id "${MACOS_NOTARY_APPLE_ID}" \
      --team-id "${MACOS_NOTARY_TEAM_ID}" \
      --password "${MACOS_NOTARY_PASSWORD}" \
      --wait
    return 0
  fi
  if [[ -n "${MACOS_NOTARY_PROFILE:-}" ]]; then
    xcrun notarytool submit "${DMG}" --keychain-profile "${MACOS_NOTARY_PROFILE}" --wait
    return 0
  fi
  echo "WARNING: no notarization credentials — DMG is only signed" >&2
  return 2
}

set +e
notarize
NOTARIZE_STATUS=$?
set -e
if [[ ${NOTARIZE_STATUS} -eq 0 ]]; then
  xcrun stapler staple "${DMG}"
  xcrun stapler validate "${DMG}"
  # Gatekeeper dry-run: fails loudly if the ticket or signature is wrong.
  spctl --assess --type open --context context:primary-signature -vv "${DMG}"
elif [[ ${NOTARIZE_STATUS} -ne 2 ]]; then
  echo "ERROR: notarization failed" >&2
  exit 1
fi

echo "DMG: ${DMG}"
