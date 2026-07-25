#!/bin/bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$DIR/dist/PlaywrightAutomationStudio"
rm -rf "$DIST" && mkdir -p "$DIST"/{data/{config,project,reports,exports},work}
mvn -B -DskipTests package
cp target/playwright-automation-studio-1.0.0.jar "$DIST/PlaywrightAutomationStudio.jar"
echo '#!/bin/bash
cd "$(dirname "$0")"
java -jar PlaywrightAutomationStudio.jar' > "$DIST/run.sh"
chmod +x "$DIST/run.sh"
echo "Portable: $DIST"
