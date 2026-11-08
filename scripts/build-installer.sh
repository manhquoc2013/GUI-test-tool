#!/bin/bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

APP_NAME="TestStudio"
APP_DISPLAY_NAME="Test Studio"
VERSION="2.2.0"
JAR_NAME="playwright-automation-studio-${VERSION}.jar"
JPACKAGE_INPUT="target/jpackage-input"
JPACKAGE_OUT="target/jpackage-out"
DIST_DIR="dist"
DMG_STAGING="target/dmg-staging"
PLAYWRIGHT_BROWSERS_PATH_ORIG="${PLAYWRIGHT_BROWSERS_PATH:-}"

OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
if [ "$ARCH" = "x86_64" ]; then ARCH="x64"; fi
if [ "$ARCH" = "aarch64" ]; then ARCH="arm64"; fi

echo "================================================================="
echo " Building ${APP_DISPLAY_NAME} Full Offline Installer (.dmg)     "
echo " Platform: ${OS} (${ARCH}) | Version: ${VERSION}                "
echo "================================================================="

# Locate JDK 26
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(mvn -q help:evaluate -Dexpression=java.home -DforceStdout 2>/dev/null || true)"
  if [ -n "${JAVA_HOME}" ] && [ ! -x "${JAVA_HOME}/bin/jpackage" ] && [ -x "${JAVA_HOME}/../bin/jpackage" ]; then
    JAVA_HOME="$(cd "${JAVA_HOME}/.." && pwd)"
  fi
  export JAVA_HOME
fi
export PATH="${JAVA_HOME}/bin:${PATH}"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "ERROR: jpackage not found. Please ensure JDK 26 is installed."
  exit 1
fi
echo "Using jpackage: $(command -v jpackage) ($(jpackage --version))"

# 1. Build application JAR + runtime dependencies
echo ""
echo "[1/7] Compiling and packaging Java application..."
mvn -DskipTests clean package

# 2. Stage jpackage inputs
echo ""
echo "[2/7] Staging jpackage input jars..."
rm -rf "${JPACKAGE_INPUT}" "${JPACKAGE_OUT}" "${DMG_STAGING}"
mkdir -p "${JPACKAGE_INPUT}/lib" "${DIST_DIR}"
cp "target/${JAR_NAME}" "${JPACKAGE_INPUT}/"
cp -R target/lib/* "${JPACKAGE_INPUT}/lib/"

# 3. Create app-image with embedded JRE
echo ""
echo "[3/7] Creating self-contained application image via jpackage..."
ICON_ARG=()
if [ -f "assets/icon.icns" ]; then
  ICON_ARG=("--icon" "assets/icon.icns")
fi

jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --input "${JPACKAGE_INPUT}" \
  --main-jar "${JAR_NAME}" \
  --main-class com.studio.App \
  --dest "${JPACKAGE_OUT}" \
  "${ICON_ARG[@]}" \
  --mac-package-name "${APP_DISPLAY_NAME}" \
  --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.instrument,jdk.compiler,jdk.unsupported \
  --java-options "-Dstudio.home=\$ROOTDIR" \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"

APP_BUNDLE="${JPACKAGE_OUT}/${APP_NAME}.app"
if [ ! -d "${APP_BUNDLE}" ]; then
  echo "ERROR: Expected .app bundle not found at: ${APP_BUNDLE}"
  exit 1
fi
CONTENT_ROOT="${APP_BUNDLE}/Contents"

# 4. Prepare directory layout inside bundle
echo ""
echo "[4/7] Assembling runtime skeleton inside bundle..."
mkdir -p "${CONTENT_ROOT}/browsers"
mkdir -p "${CONTENT_ROOT}/tools/node/bin"
mkdir -p "${CONTENT_ROOT}/tools/playwright-cli"
mkdir -p "${CONTENT_ROOT}/data/project"
mkdir -p "${CONTENT_ROOT}/data/reports"
mkdir -p "${CONTENT_ROOT}/data/config"
mkdir -p "${CONTENT_ROOT}/data/exports"
mkdir -p "${CONTENT_ROOT}/work"

if [ -f "data/project/.gitkeep" ]; then
  cp "data/project/.gitkeep" "${CONTENT_ROOT}/data/project/.gitkeep"
fi

# 5. Bundle Node.js LTS portable
echo ""
echo "[5/7] Bundling Node.js LTS portable..."
NODE_VERSION="20.18.0"
NODE_ARCHIVE="node-v${NODE_VERSION}-${OS}-${ARCH}.tar.gz"
NODE_URL="https://nodejs.org/dist/v${NODE_VERSION}/${NODE_ARCHIVE}"
NODE_DIR="${CONTENT_ROOT}/tools/node"

if [ ! -f "/tmp/${NODE_ARCHIVE}" ]; then
  echo "  Downloading Node.js ${NODE_VERSION}..."
  curl -fsSL -o "/tmp/${NODE_ARCHIVE}" "${NODE_URL}"
fi

echo "  Extracting Node.js into bundle..."
tar -xzf "/tmp/${NODE_ARCHIVE}" -C /tmp/
rm -rf "${NODE_DIR:?}/"*
cp -R /tmp/node-v${NODE_VERSION}-${OS}-${ARCH}/* "${NODE_DIR}/"
chmod -R +x "${NODE_DIR}/bin" 2>/dev/null || true

# 6. Bundle Playwright CLI & Offline Chromium
echo ""
echo "[6/7] Bundling Playwright CLI and offline Chromium browser..."
export PATH="${NODE_DIR}/bin:$PATH"
PW_CLI_DIR="${CONTENT_ROOT}/tools/playwright-cli"
cd "${PW_CLI_DIR}"
npm init -y > /dev/null 2>&1
npm install playwright@1.61.0 > /dev/null 2>&1
cd "$DIR"

export PLAYWRIGHT_BROWSERS_PATH="${CONTENT_ROOT}/browsers"

# Check host cache to speed up / avoid network download
HOST_PW_CACHE=""
if [ -n "${PLAYWRIGHT_BROWSERS_PATH_ORIG:-}" ] && [ -d "${PLAYWRIGHT_BROWSERS_PATH_ORIG}" ]; then
  HOST_PW_CACHE="${PLAYWRIGHT_BROWSERS_PATH_ORIG}"
elif [ -d "$HOME/Library/Caches/ms-playwright" ]; then
  HOST_PW_CACHE="$HOME/Library/Caches/ms-playwright"
elif [ -d "$HOME/.cache/ms-playwright" ]; then
  HOST_PW_CACHE="$HOME/.cache/ms-playwright"
fi

if [ -n "$HOST_PW_CACHE" ] && ls "${HOST_PW_CACHE}"/chromium* >/dev/null 2>&1; then
  echo "  Pre-populating Chromium & FFMPEG from local cache (${HOST_PW_CACHE})..."
  cp -R "${HOST_PW_CACHE}"/chromium* "${CONTENT_ROOT}/browsers/" 2>/dev/null || true
  cp -R "${HOST_PW_CACHE}"/ffmpeg* "${CONTENT_ROOT}/browsers/" 2>/dev/null || true
fi

if ! ls "${CONTENT_ROOT}/browsers"/chromium* >/dev/null 2>&1; then
  echo "  Downloading Chromium into bundled browsers directory..."
  npx --prefix "${PW_CLI_DIR}" playwright install chromium
fi

# Ensure Playwright Java driver matches and verifies Chromium
echo "  Verifying offline Chromium with Playwright Java..."
mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium" || true

if ! ls "${CONTENT_ROOT}/browsers"/chromium* >/dev/null 2>&1; then
  echo "ERROR: Failed to bundle Chromium into ${CONTENT_ROOT}/browsers! Installer would be incomplete."
  exit 1
fi
chmod -R +x "${CONTENT_ROOT}/browsers" 2>/dev/null || true
echo "  ✓ Bundled Chromium verified in: ${CONTENT_ROOT}/browsers"

# 7. Package macOS DMG Installer
echo ""
echo "[7/7] Generating macOS Drag-and-Drop DMG Installer..."
DMG_NAME="${APP_NAME}-${VERSION}-mac-${ARCH}.dmg"
DMG_OUTPUT="${DIST_DIR}/${DMG_NAME}"

mkdir -p "${DMG_STAGING}"
cp -R "${APP_BUNDLE}" "${DMG_STAGING}/"

# Add Applications shortcut for drag-and-drop install
ln -s /Applications "${DMG_STAGING}/Applications"

# Add README note in installer
cat > "${DMG_STAGING}/README.txt" << 'EOF'
Test Studio
===========

Installation:
- Drag "TestStudio" to the "Applications" shortcut icon.
- Open Applications and click to launch.

Full Offline Edition:
- Bundled with Java 26 Runtime (Temurin).
- Bundled with Node.js LTS and Playwright Chromium.
- Zero network or Java installation required.
EOF

# Remove previous DMG if exists
rm -f "${DMG_OUTPUT}"

# Create compressed read-only DMG with hdiutil
hdiutil create \
  -volname "${APP_DISPLAY_NAME}" \
  -srcfolder "${DMG_STAGING}" \
  -ov \
  -format UDZO \
  "${DMG_OUTPUT}"

# Clean staging
rm -rf "${DMG_STAGING}"

FILE_SIZE=$(du -h "${DMG_OUTPUT}" | cut -f1)

echo ""
echo "================================================================="
echo " BUILD SUCCESSFUL!                                              "
echo " Installer (.dmg): ${DMG_OUTPUT} (${FILE_SIZE})                "
echo " Standalone .app:  ${APP_BUNDLE}                                "
echo "================================================================="
echo "Users can double-click the .dmg and drag into Applications to run!"

