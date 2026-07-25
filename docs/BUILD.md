# Build Guide

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | **26** | Temurin recommended. Must include `javac` (JDK, not JRE). |
| Maven | **3.9+** | Bundled `mvnw` wrapper not included; use system Maven. |
| Node.js | 20+ (optional) | Only needed for Playwright codegen in dev mode. |
| Git | any | Required for clone only. |

Verify your environment:

```bash
java -version        # Should show 26
mvn -version         # Should show 3.9+
```

---

## Development Build

### Compile only

```bash
mvn -DskipTests compile
```

Output in `target/classes/`.

### Package JAR

```bash
mvn -DskipTests package
```

Produces `target/playwright-automation-studio-1.0.0.jar`.

### Run the application

```bash
mvn javafx:run
```

The JavaFX Maven plugin (`org.openjfx:javafx-maven-plugin:0.0.8`) handles module path resolution automatically. The application launches at 1200×800 with the Catppuccin Mocha dark theme.

In dev mode, `APP_HOME` resolves to the Maven project root (where `pom.xml` lives). All runtime data goes into `data/` and `work/` at that root.

---

## Portable Build

The portable build produces a **self-contained directory** that can be zipped and distributed to end users who have **nothing installed** — no JDK, no Node.js, no Maven.

### macOS / Linux

```bash
bash scripts/build-portable.sh
```

### Windows

```cmd
scripts\build-portable.bat
```

### What the script does

1. Runs `mvn -DskipTests package` to produce the application JAR.
2. Uses `jpackage --type app-image` to create a self-contained app image with an embedded JRE and JavaFX modules.
3. Copies the app image into `dist/PlaywrightAutomationStudio-<OS>-<ARCH>/`.
4. Downloads **Node.js 20.18.0 LTS** portable into `tools/node/`.
5. Installs Playwright CLI locally in `tools/playwright-cli/` (via npm).
6. Installs Playwright Chromium into `browsers/` (root of the image).
7. Zips the resulting directory.

### Output

```
dist/PlaywrightAutomationStudio-<OS>-<ARCH>.zip
```

### Portable directory layout

```
PlaywrightAutomationStudio-<os>-<arch>/
├── PlaywrightAutomationStudio[.exe]    # Launcher (double-click to run)
├── runtime/                            # Embedded JRE (jlink)
├── app/                                # Application JAR + dependencies
│   ├── PlaywrightAutomationStudio.jar
│   └── lib/
├── tools/
│   ├── node/bin/node[.exe]             # Node.js portable (for codegen)
│   └── playwright-cli/                 # Playwright npm package
├── browsers/                           # Chromium (PLAYWRIGHT_BROWSERS_PATH)
├── data/                               # Runtime data (empty on first run)
├── work/                               # Temp directory
└── README-PORTABLE.txt                 # "Extract → run launcher"
```

### Client instructions (portable)

1. Extract the `.zip` file.
2. Double-click the launcher (`PlaywrightAutomationStudio` or `.exe`).
3. No installation required.

### Known constraints

- `jpackage` does **not** cross-compile. Build macOS portable on macOS, Windows on Windows, Linux on Linux.
- The portable bundle includes only **Chromium** by default. Firefox and WebKit can be added by modifying the build script.
- Estimated uncompressed size: several hundred MB to 1+ GB (JRE + Chromium + Node).

---

## CI/CD

### `.github/workflows/ci.yml`

Triggers on every **push** and **pull request** to `main`:

- Checkout → Setup JDK 26 → Cache Maven → `mvn -B -DskipTests package`
- PR fails if compilation fails.

### `.github/workflows/release-portable.yml`

Triggers on **tag** (e.g., `v1.0.0`) or manual `workflow_dispatch`:

| Runner | Artifact |
|--------|----------|
| `macos-latest` | `PlaywrightAutomationStudio-mac-*.zip` |
| `windows-latest` | `PlaywrightAutomationStudio-win-*.zip` |
| `ubuntu-latest` | `PlaywrightAutomationStudio-linux-*.zip` |

Each job:

1. Setup JDK 26 + Node.js
2. `mvn -B package`
3. Run platform-specific `scripts/build-portable.*`
4. Upload artifact via `actions/upload-artifact` and attach to GitHub Release.

### Required permissions

- `contents: write` for publishing releases.

---

## Project Structure (key directories only)

```
APP_HOME/
├── pom.xml                                  # Maven project descriptor
├── src/main/java/com/studio/
│   ├── App.java                             # JavaFX Application entry point
│   ├── model/                               # Data classes
│   ├── store/                               # Filesystem CRUD operations
│   ├── service/                             # Core business logic
│   ├── controller/                          # FXML-backed UI controllers
│   ├── runtime/                             # Thread-local Playwright state
│   └── util/                                # Path resolution, browser detection
├── src/main/resources/
│   ├── fxml/*.fxml                          # UI layouts (6 files)
│   └── css/studio.css                       # Catppuccin Mocha theme
├── data/                                    # Runtime user data
│   ├── config/settings.json
│   ├── project/                             # Group/Suite/Case mirror tree
│   ├── reports/                             # Run history
│   └── exports/                             # Excel export default location
├── work/                                    # Temporary compile/harness output
├── scripts/                                 # Build scripts
├── .github/workflows/                       # CI/CD pipelines
└── docs/                                    # Project documentation
```
