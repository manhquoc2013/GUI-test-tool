#!/usr/bin/env bash
# Build PyInstaller onedir + portable ZIP (Chromium not bundled).
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case "${ARCH}" in
  x86_64) ARCH="x64" ;;
  aarch64|arm64) ARCH="arm64" ;;
esac

uv sync --extra build
META_JSON="$(uv run python -m ai_qa_test_studio --print-metadata-json)"
VERSION="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["version"])')"
EXE_NAME="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["exe_name"])')"

echo "=== Packaging ${EXE_NAME} v${VERSION} (${OS}-${ARCH}) ==="

rm -rf build "dist/${EXE_NAME}" "dist/${EXE_NAME}.app"
uv run pyinstaller --noconfirm packaging/AIQATestStudio.spec

ZIP_NAME="${EXE_NAME}-${VERSION}-${OS}-${ARCH}.zip"
rm -f "dist/${ZIP_NAME}"

if [[ "${OS}" == "darwin" && -d "dist/${EXE_NAME}.app" ]]; then
  (
    cd dist
    zip -r "${ZIP_NAME}" "${EXE_NAME}.app"
  )
elif [[ -d "dist/${EXE_NAME}" ]]; then
  (
    cd dist
    zip -r "${ZIP_NAME}" "${EXE_NAME}"
  )
else
  echo "ERROR: PyInstaller output not found under dist/"
  ls -la dist || true
  exit 1
fi

printf '%s\n' "${META_JSON}" > dist/metadata.json
echo "Portable zip: dist/${ZIP_NAME}"
echo "=== Package complete ==="
