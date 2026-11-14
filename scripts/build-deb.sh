#!/usr/bin/env bash
# Package PyInstaller onedir as a Debian package under /opt/...
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

META_JSON="$(uv run python -m ai_qa_test_studio --print-metadata-json)"
VERSION="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["version"])')"
PUBLISHER="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["publisher"])')"
EMAIL="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["publisher_email"])')"
EXE_NAME="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["exe_name"])')"
DISPLAY="$(printf '%s' "${META_JSON}" | uv run python -c 'import json,sys; print(json.load(sys.stdin)["display_name"])')"
ARCH_RAW="$(dpkg --print-architecture 2>/dev/null || uname -m)"
case "${ARCH_RAW}" in
  x86_64|amd64) DEB_ARCH="amd64"; ZIP_ARCH="x64" ;;
  aarch64|arm64) DEB_ARCH="arm64"; ZIP_ARCH="arm64" ;;
  *) DEB_ARCH="${ARCH_RAW}"; ZIP_ARCH="${ARCH_RAW}" ;;
esac

ONEDIR="dist/${EXE_NAME}"
if [[ ! -d "${ONEDIR}" ]]; then
  echo "ERROR: missing ${ONEDIR} — run scripts/package_app.sh first"
  exit 1
fi

PKG_ROOT="dist/deb-root"
OPT_DIR="${PKG_ROOT}/opt/ai-qa-test-studio"
BIN_DIR="${PKG_ROOT}/usr/local/bin"
APP_DIR="${PKG_ROOT}/usr/share/applications"
rm -rf "${PKG_ROOT}"
mkdir -p "${OPT_DIR}" "${BIN_DIR}" "${APP_DIR}" "${PKG_ROOT}/DEBIAN"

cp -a "${ONEDIR}/." "${OPT_DIR}/"
chmod -R a+rX "${OPT_DIR}"
# Ensure nested driver node is executable
find "${OPT_DIR}" -type f \( -name node -o -name "${EXE_NAME}" \) -exec chmod 755 {} +
find "${OPT_DIR}" -type f -perm -111 -exec chmod 755 {} +

ln -sfn "/opt/ai-qa-test-studio/${EXE_NAME}" "${BIN_DIR}/ai-qa-test-studio"

cat > "${APP_DIR}/ai-qa-test-studio.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${DISPLAY}
Exec=/usr/local/bin/ai-qa-test-studio
Icon=/opt/ai-qa-test-studio/${EXE_NAME}
Categories=Development;Programming;
Terminal=false
EOF

cat > "${PKG_ROOT}/DEBIAN/control" <<EOF
Package: ai-qa-test-studio
Version: ${VERSION}
Section: devel
Priority: optional
Architecture: ${DEB_ARCH}
Maintainer: ${PUBLISHER} <${EMAIL}>
Description: ${DISPLAY}
 Autonomous Shift-Left MCP-powered AI QA desktop studio.
EOF

DEB="dist/${EXE_NAME}-${VERSION}-linux-${ZIP_ARCH}.deb"
rm -f "${DEB}"
fakeroot dpkg-deb --build "${PKG_ROOT}" "${DEB}"
echo "DEB: ${DEB}"
