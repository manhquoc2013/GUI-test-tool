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
mvn -version         # Should show 3.9+ AND "Java version: 26"
```

> Maven runs on the JDK that `JAVA_HOME` (or the first JDK on `PATH`) points to, which is **not** necessarily the one `java -version` reports in every shell. If `mvn -version` shows 17 or 21, Maven is running on the wrong JDK even though Java 26 is installed.
>
> The build fails fast with `Build requires JDK 26 …` (maven-enforcer) when it detects a JDK older than the compiler release. Point `JAVA_HOME` at a JDK 26 install, e.g. on macOS:
>
> ```bash
> export JAVA_HOME="$(/usr/libexec/java_home -v 26)"
> ```

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

Produces `target/playwright-automation-studio-2.6.0.jar`.

### Run the application

```bash
mvn javafx:run
```

The JavaFX Maven plugin (`org.openjfx:javafx-maven-plugin:0.0.8`) handles module path resolution automatically. The application launches at 1200×800 with the Catppuccin Mocha dark theme.

In dev mode, `APP_HOME` resolves to the Maven project root (where `pom.xml` lives). All runtime data goes into `data/` and `work/` at that root.

---

## Offline Installers (DMG & Setup EXE)

In addition to portable archives, Test Studio provides native offline installers for macOS and Windows. These bundle a dedicated JRE 26, Node.js LTS, Playwright CLI, and offline Chromium, requiring zero network connection or pre-installed software on the client machine.

### macOS Drag-and-Drop DMG Installer

```bash
bash scripts/build-installer.sh
```

- Produces: `dist/TestStudio-2.6.0-mac-<arch>.dmg`
- Bundles a complete `.app` bundle, embedded JRE 26, Node.js, and offline Chromium.
- Provides standard `/Applications` drag-and-drop link and icon branding.

### Windows Offline Setup Wizard (.exe)

```cmd
scripts\build-installer.bat
```

- Produces: `dist\TestStudio-2.6.0-windows-x64-Setup.exe`
- Uses Inno Setup 6 (with automatic fallback to `jpackage --type exe`).
- Creates Start Menu entries, optional Desktop shortcut, and registered Windows uninstaller.

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

## CI/CD (Release Workflow)

### `.github/workflows/release-portable.yml`

Quy trình CI/CD hoàn toàn tự động cho 3 nền tảng (**macOS**, **Windows**, **Ubuntu Linux**), **chỉ chạy khi phát hành phiên bản**:
- **Triggers**:
  - Khi push tag phiên bản: `git tag v1.0.0 && git push origin v1.0.0`
  - Khi tạo/publish Release trên GitHub: `release: [published]`
  - Khi kích hoạt thủ công trong tab Actions: `workflow_dispatch` (cho phép nhập version tag tùy chọn)

| Runner | Hệ điều hành mục tiêu | Artifact Portable Output |
|--------|-----------------------|--------------------------|
| `macos-latest` | macOS (Apple Silicon / arm64) | `PlaywrightAutomationStudio-Darwin-*.zip` |
| `windows-latest` | Windows (x64) | `PlaywrightAutomationStudio-win-x64.zip` |
| `ubuntu-latest` | Ubuntu Linux (x64) | `PlaywrightAutomationStudio-Linux-*.zip` |

**Quy trình thực thi:**
1. Setup JDK 26 (Temurin) + Node.js 22.
2. Kiểm tra công cụ jpackage.
3. Chạy toàn bộ bộ kiểm thử tự động (**260 unit tests**) với Xvfb trên Linux để bảo đảm chất lượng tuyệt đối trước khi đóng gói.
4. Chạy `scripts/build-portable.sh` / `scripts/build-portable.bat` để đóng gói jpackage app-image kèm JRE nhúng, Node.js và Playwright Chromium.
5. Upload artifacts của 3 hệ điều hành (bao gồm cả bản Portable ZIP và Native Installers: `.exe`, `.dmg`, `.deb`).
6. Job `publish` gom toàn bộ các tệp phân phối và đính kèm vào mục Assets của GitHub Release chính thức kèm Release Notes được trích xuất tự động theo phiên bản.

### Required permissions
- `contents: write` để tự động tạo và tải assets lên GitHub Releases.

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
