#!/bin/bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

APP_NAME="TestStudio"
VERSION="$(python3 -c "import xml.etree.ElementTree as ET; print(ET.parse('pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version').text)" 2>/dev/null || true)"
if [ -z "${VERSION}" ]; then
  VERSION="$(sed -n 's/.*<version>\([0-9][^<]*\)<\/version>.*/\1/p' pom.xml 2>/dev/null | head -n 1)"
fi
if [ -z "${VERSION}" ]; then VERSION="2.4.0"; fi
JAR_NAME="playwright-automation-studio-${VERSION}.jar"
DIST_ROOT="dist/${APP_NAME}"
JPACKAGE_INPUT="target/jpackage-input"
JPACKAGE_OUT="target/jpackage-out"
PLAYWRIGHT_BROWSERS_PATH_ORIG="${PLAYWRIGHT_BROWSERS_PATH:-}"

echo "=== Building ${APP_NAME} Portable (jpackage app-image) ==="

# Prefer the same JDK Maven uses (required: jpackage must match compile release)
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(mvn -q help:evaluate -Dexpression=java.home -DforceStdout 2>/dev/null || true)"
  # java.home may point at a jre subdir; climb to JDK home if needed
  if [ -n "${JAVA_HOME}" ] && [ ! -x "${JAVA_HOME}/bin/jpackage" ] && [ -x "${JAVA_HOME}/../bin/jpackage" ]; then
    JAVA_HOME="$(cd "${JAVA_HOME}/.." && pwd)"
  fi
  export JAVA_HOME
fi
export PATH="${JAVA_HOME}/bin:${PATH}"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "ERROR: jpackage not found. Install a full JDK 26 (not a JRE-only runtime)."
  exit 1
fi
echo "Using jpackage: $(command -v jpackage) ($(jpackage --version))"

# 1. Build JAR + runtime deps
echo "Step 1: Building application..."
mvn -DskipTests clean package

# 2. Stage jpackage input (main jar + lib/)
echo "Step 2: Staging jpackage input..."
rm -rf "${JPACKAGE_INPUT}" "${JPACKAGE_OUT}" dist
mkdir -p "${JPACKAGE_INPUT}/lib"
cp "target/${JAR_NAME}" "${JPACKAGE_INPUT}/"
cp -R target/lib/* "${JPACKAGE_INPUT}/lib/"

# 3. jpackage app-image with embedded runtime
echo "Step 3: Creating app-image with embedded JRE (including javac compiler)..."
jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --input "${JPACKAGE_INPUT}" \
  --main-jar "${JAR_NAME}" \
  --main-class com.studio.Launcher \
  --dest "${JPACKAGE_OUT}" \
  --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.instrument,java.scripting,java.xml,java.net.http,jdk.compiler,jdk.unsupported \
  --java-options "-Dstudio.home=\$ROOTDIR" \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"

# Locate produced image (macOS .app vs linux dir)
IMAGE_PATH=""
if [ -d "${JPACKAGE_OUT}/${APP_NAME}.app" ]; then
  IMAGE_PATH="${JPACKAGE_OUT}/${APP_NAME}.app"
  CONTENT_ROOT="${IMAGE_PATH}/Contents"
elif [ -d "${JPACKAGE_OUT}/${APP_NAME}" ]; then
  IMAGE_PATH="${JPACKAGE_OUT}/${APP_NAME}"
  CONTENT_ROOT="${IMAGE_PATH}"
else
  echo "ERROR: jpackage output not found under ${JPACKAGE_OUT}"
  ls -la "${JPACKAGE_OUT}" || true
  exit 1
fi

# 4. Assemble portable distribution beside launcher
echo "Step 4: Assembling portable layout..."
mkdir -p "${DIST_ROOT}"
cp -R "${IMAGE_PATH}" "${DIST_ROOT}/"

# Resolve where data/tools/browsers live (same root jpackage uses as $ROOTDIR)
if [ -d "${DIST_ROOT}/${APP_NAME}.app" ]; then
  BUNDLE_ROOT="${DIST_ROOT}/${APP_NAME}.app/Contents"
  LAUNCHER_HINT="./${APP_NAME}.app/Contents/MacOS/${APP_NAME}"
elif [ -d "${DIST_ROOT}/${APP_NAME}" ]; then
  BUNDLE_ROOT="${DIST_ROOT}/${APP_NAME}"
  LAUNCHER_HINT="./${APP_NAME}/${APP_NAME}"
else
  BUNDLE_ROOT="${DIST_ROOT}"
  LAUNCHER_HINT="./${APP_NAME}"
fi

mkdir -p "${BUNDLE_ROOT}/data/project"
mkdir -p "${BUNDLE_ROOT}/data/reports"
mkdir -p "${BUNDLE_ROOT}/data/config"
mkdir -p "${BUNDLE_ROOT}/data/exports"
mkdir -p "${BUNDLE_ROOT}/work"
mkdir -p "${BUNDLE_ROOT}/browsers"
mkdir -p "${BUNDLE_ROOT}/tools/node/bin"
mkdir -p "${BUNDLE_ROOT}/tools/playwright-cli"

# Empty project tree only — never ship local developer project data
if [ -f "data/project/.gitkeep" ]; then
  cp "data/project/.gitkeep" "${BUNDLE_ROOT}/data/project/.gitkeep"
fi

# 5. Bundle Node.js LTS
echo "Step 5: Setting up Node.js portable..."
NODE_VERSION="20.18.0"
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
if [ "$ARCH" = "x86_64" ]; then ARCH="x64"; fi
if [ "$ARCH" = "aarch64" ]; then ARCH="arm64"; fi

NODE_ARCHIVE="node-v${NODE_VERSION}-${OS}-${ARCH}.tar.gz"
NODE_URL="https://nodejs.org/dist/v${NODE_VERSION}/${NODE_ARCHIVE}"
NODE_DIR="${BUNDLE_ROOT}/tools/node"

if [ ! -f "/tmp/${NODE_ARCHIVE}" ]; then
  echo "  Downloading Node.js ${NODE_VERSION}..."
  curl -L -o "/tmp/${NODE_ARCHIVE}" "${NODE_URL}"
fi

if [ ! -d "/tmp/node-v${NODE_VERSION}-${OS}-${ARCH}" ]; then
  echo "  Extracting Node.js..."
  tar -xzf "/tmp/${NODE_ARCHIVE}" -C /tmp/
fi
rm -rf "${NODE_DIR:?}/"*
cp -R /tmp/node-v${NODE_VERSION}-${OS}-${ARCH}/* "${NODE_DIR}/"
chmod -R +x "${NODE_DIR}/bin" 2>/dev/null || true

# 6. Playwright CLI + browsers
echo "Step 6: Installing Playwright CLI + Chromium (Offline bundle)..."
export PATH="${NODE_DIR}/bin:$PATH"
PW_CLI_DIR="${BUNDLE_ROOT}/tools/playwright-cli"
cd "${PW_CLI_DIR}"
npm init -y > /dev/null 2>&1
npm install playwright@1.61.0 > /dev/null 2>&1
cd "$DIR"

export PLAYWRIGHT_BROWSERS_PATH="${BUNDLE_ROOT}/browsers"

# Check if Chromium already exists in host cache to speed up / avoid network fetch
HOST_PW_CACHE=""
if [ -n "${PLAYWRIGHT_BROWSERS_PATH_ORIG:-}" ] && [ -d "${PLAYWRIGHT_BROWSERS_PATH_ORIG}" ]; then
  HOST_PW_CACHE="${PLAYWRIGHT_BROWSERS_PATH_ORIG}"
elif [ -d "$HOME/Library/Caches/ms-playwright" ]; then
  HOST_PW_CACHE="$HOME/Library/Caches/ms-playwright"
elif [ -d "$HOME/.cache/ms-playwright" ]; then
  HOST_PW_CACHE="$HOME/.cache/ms-playwright"
fi

if [ -n "$HOST_PW_CACHE" ] && ls "${HOST_PW_CACHE}"/chromium* >/dev/null 2>&1; then
  echo "  Pre-populating Chromium from host cache (${HOST_PW_CACHE})..."
  cp -R "${HOST_PW_CACHE}"/chromium* "${BUNDLE_ROOT}/browsers/" 2>/dev/null || true
  cp -R "${HOST_PW_CACHE}"/ffmpeg* "${BUNDLE_ROOT}/browsers/" 2>/dev/null || true
fi

if ! ls "${BUNDLE_ROOT}/browsers"/chromium* >/dev/null 2>&1; then
  echo "  Downloading Chromium into bundled browsers directory..."
  npx --prefix "${PW_CLI_DIR}" playwright install chromium
fi

# Ensure Playwright Java driver matches and verifies Chromium
echo "  Verifying Chromium with Playwright Java CLI..."
mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium" || true

# Verify bundling succeeded
if ! ls "${BUNDLE_ROOT}/browsers"/chromium* >/dev/null 2>&1; then
  echo "ERROR: Failed to bundle Chromium into ${BUNDLE_ROOT}/browsers! Portable distribution incomplete."
  exit 1
fi
chmod -R +x "${BUNDLE_ROOT}/browsers" 2>/dev/null || true
echo "  ✓ Bundled Chromium verified in: ${BUNDLE_ROOT}/browsers"

# 7. Convenience launcher that exports PLAYWRIGHT_BROWSERS_PATH (no host java)
echo "Step 7: Writing convenience launcher..."
cat > "${DIST_ROOT}/run.sh" << EOF
#!/bin/bash
DIR="\$(cd "\$(dirname "\$0")" && pwd)"
export PLAYWRIGHT_BROWSERS_PATH="\${DIR}/${BUNDLE_ROOT#"${DIST_ROOT}/"}/browsers"
export PATH="\${DIR}/${BUNDLE_ROOT#"${DIST_ROOT}/"}/tools/node/bin:\$PATH"
exec "\${DIR}/${LAUNCHER_HINT#./}" "\$@"
EOF
# Fix run.sh paths more reliably
if [ -d "${DIST_ROOT}/${APP_NAME}.app" ]; then
  cat > "${DIST_ROOT}/run.sh" << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
export PLAYWRIGHT_BROWSERS_PATH="$DIR/TestStudio.app/Contents/browsers"
export PATH="$DIR/TestStudio.app/Contents/tools/node/bin:$PATH"
exec "$DIR/TestStudio.app/Contents/MacOS/TestStudio" "$@"
EOF
else
  cat > "${DIST_ROOT}/run.sh" << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
export PLAYWRIGHT_BROWSERS_PATH="$DIR/TestStudio/browsers"
export PATH="$DIR/TestStudio/tools/node/bin:$PATH"
exec "$DIR/TestStudio/TestStudio" "$@"
EOF
fi
chmod +x "${DIST_ROOT}/run.sh"

cat > "${DIST_ROOT}/README.txt" << 'README'
Test Studio - Portable Edition
==============================

To run:
  macOS/Linux: ./run.sh   (or open the app-image launcher)
  Windows:     run.bat

This build embeds a full Java runtime via jpackage — no host JDK/JRE required.
Node.js and Playwright Chromium are also bundled.

Data is stored under the app-image Contents/root data/ directory.
The work/ directory contains temporary files and can be safely deleted.
README

# 8. Zip
echo "Step 8: Creating zip archive..."
cd dist
ZIP_NAME="${APP_NAME}-$(uname -s)-$(uname -m).zip"
zip -r "${ZIP_NAME}" "${APP_NAME}/"
echo "Portable zip created: dist/${ZIP_NAME}"
cd ..

echo "=== Build Complete ==="
echo "Portable app: dist/${APP_NAME}/"
echo "Zip archive:  dist/${ZIP_NAME}"
echo "Launcher uses embedded runtime (no host java on PATH needed)."
