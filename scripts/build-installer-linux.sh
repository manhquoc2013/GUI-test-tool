#!/bin/bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

APP_NAME="TestStudio"
APP_DISPLAY_NAME="Test Studio"
VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null || echo '2.3.0')"
if [ -z "${VERSION}" ]; then VERSION="2.3.0"; fi
DIST_DIR="dist"

echo "================================================================="
echo " Building ${APP_DISPLAY_NAME} Linux Installer (.deb)            "
echo " Platform: Linux (x64) | Version: ${VERSION}                    "
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

# Resolve application bundle with bundled offline browsers and runtime
APP_IMAGE_DIR=""
if [ -d "dist/${APP_NAME}/${APP_NAME}/browsers" ]; then
  APP_IMAGE_DIR="dist/${APP_NAME}/${APP_NAME}"
elif [ -d "target/jpackage-out/${APP_NAME}/browsers" ]; then
  APP_IMAGE_DIR="target/jpackage-out/${APP_NAME}"
else
  echo "Staging complete application bundle via build-portable.sh..."
  bash scripts/build-portable.sh
  APP_IMAGE_DIR="dist/${APP_NAME}/${APP_NAME}"
fi

if [ ! -d "${APP_IMAGE_DIR}" ] || [ ! -d "${APP_IMAGE_DIR}/browsers" ]; then
  echo "ERROR: Application image at ${APP_IMAGE_DIR} does not contain bundled offline browsers."
  exit 1
fi

mkdir -p "${DIST_DIR}"

ICON_ARG=()
if [ -f "assets/icon.png" ]; then
  ICON_ARG=("--icon" "assets/icon.png")
fi

echo "Creating Debian package (.deb) via jpackage..."
jpackage \
  --type deb \
  --name "teststudio" \
  --app-version "${VERSION}" \
  --app-image "${APP_IMAGE_DIR}" \
  --dest "${DIST_DIR}" \
  --description "Test Studio - Playwright Automation Desktop IDE" \
  --vendor "Test Studio Team" \
  --linux-shortcut \
  --linux-menu-group "Development;Testing;" \
  --linux-package-name "teststudio" \
  "${ICON_ARG[@]}"

echo "================================================================="
echo " Linux Installer (.deb) created successfully in ${DIST_DIR}/"
ls -la "${DIST_DIR}"/*.deb || true
echo "================================================================="

