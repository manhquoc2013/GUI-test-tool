# Playwright Automation Studio

> A JavaFX desktop application for managing and running Playwright test automation — no code required.

[![Java](https://img.shields.io/badge/Java-26-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![JavaFX](https://img.shields.io/badge/JavaFX-26.0.1-5382A1?logo=openjdk&logoColor=white)](https://openjfx.io/)
[![Playwright](https://img.shields.io/badge/Playwright-1.61.0-2DAD59?logo=playwright&logoColor=white)](https://playwright.dev/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Screenshot

The application presents a three-panel layout:

- **Left sidebar** — navigation between Explorer, Reports, and AI Assistant views, active suite indicator, and Run All trigger.
- **Center content** — the active page: Explorer (tree of Groups → Suites → Cases), Suite Workspace (code editor, Record/Play controls, terminal), or Reports (run history and detail).
- **Toast overlay** — real-time feedback for every action (CRUD, Record, Run, Export) in success/info/warn/error styles.

All UI is styled with the [Catppuccin Mocha](https://github.com/catppuccin/catppuccin) dark theme.

---

## Features

- **AI Assistant with OpenAI Integration** — Built-in AI assistant powered by OpenAI models (`gpt-4o`, `gpt-4o-mini`, `o3-mini`, `o1`) or compatible endpoints (Azure, OpenRouter, LocalAI, Ollama):
  - **Natural language test generation**: Describe your test scenarios in plain text, and AI generates full Playwright Java scripts and structured assertions.
  - **Multi-format document parsing**: Upload requirements or specifications directly from `.txt`, `.md`, `.json`, `.csv`, Word (`.docx`), PDF (`.pdf`), and UI screenshots/mockups (`.png`, `.jpg`).
  - **One-click Import & Update**: Preview generated cases and import them directly into any Group/Suite or update the active Case.
  - **In-Workspace AI Assist**: Quick prompt modal inside Suite Workspace (`✨ AI Hỗ trợ`) to refine scripts or add assertions on the fly.
  - **Flexible Configuration**: Configure API Key (or fallback to `OPENAI_API_KEY` env var), custom Base URL, temperature, and test connection latency with one click.
- **Visual test tree management** — Organize tests in a three-level hierarchy: **Group → Suite → Case**. Inline CRUD with rename, delete, duplicate, and drag-and-drop reorder.
- **Record browser interactions** — Record via Playwright codegen in Chromium, Firefox, WebKit, Chrome, or Edge. Close the browser and the **Assertion Builder** opens automatically.
- **46 assertion types** — Catalog of assertions across 8 categories (page content, elements, images/media, file/downloads, tables, forms, page/network state, custom JS). Visual builder with status management (DRAFT / ACTIVE / INACTIVE).
- **4 run scopes** — **Run All** (entire project), **Run Group** (recursive), **Run Suite**, **Run Case**. Each produces a detailed RunReport.
- **Shared browser context** — Multi-case runs (Suite/Group/All) share a single BrowserContext by default — cookies, localStorage, and sessions persist across cases. Toggle off for isolation.
- **Soft-assert with pass/fail tracking** — Assertion failures are aggregated per case; every active assert runs to completion. Detailed report shows which assertions passed, failed, or were skipped and why.
- **Instant Post-Run Reporting & Evidence Navigation** — Immediately navigates to the detailed Report & Evidence view upon test execution completion (Run All, Group, Suite, or Case).
- **Screenshot Evidence & High-Resolution Viewer** — Every executed case produces a screenshot (PASSED or FAILED). Viewable inline, via a dedicated "📸 Bằng chứng" column, or through the interactive full-screen Evidence Viewer modal with zoom and desktop open options.
- **Standalone HTML Report Export** — Export self-contained single-file HTML reports with embedded Base64 screenshot evidence, interactive pass/fail filters, KPI summary cards, and assertion breakdowns for offline viewing and sharing.
- **Excel Export** — Three-sheet workbook (Summary, Cases, Asserts) exportable from any report detail or history list.
- **Toast notification system** — Every user action produces immediate non-blocking feedback (success / info / warn / error).
- **Cross-platform browser detection** — Automatically detects Playwright-bundled Chromium, Firefox, WebKit, plus system-installed Chrome and Edge channels.
- **Collapsible sidebar** — Sidebar collapses to a 44px strip for maximum workspace area. Terminal auto-shows on Record/Run.
- **Portable distribution** — Self-contained bundle with embedded JRE, Node.js LTS, Playwright CLI, and Chromium browser. No installation required.
- **GitHub Actions CI/CD** — Build verification and matrix portable releases (macOS, Windows, Linux) only on version tags (`v*`).
- **Catppuccin Mocha dark theme** — Consistent, comfortable dark UI.

---

## Quick Start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK (Temurin recommended) | 26 |
| Maven | 3.9+ |
| (Optional) Node.js | 20+ (for Playwright codegen in dev mode) |

### Run

```bash
git clone <repository-url>
cd playwright-automation-studio
mvn javafx:run
```

The application launches with an empty project tree. Start by creating a Group, then a Suite, then a Case. Record interactions with the **Record** button or add assertions manually.

### Portable (no-JDK) build

```bash
# macOS / Linux
bash scripts/build-portable.sh

# Windows
scripts\build-portable.bat
```

The portable build runs `jpackage --type app-image` to embed a full Java runtime, then bundles Node.js and Playwright Chromium. Extract `dist/PlaywrightAutomationStudio-<OS>-<ARCH>.zip` and run `./run.sh` (or `run.bat` / the native launcher). **No host JDK, Node, or Maven** is required on the target machine — the build machine needs JDK 26 with `jpackage`.

---

## Project Structure

```
APP_HOME/
├── pom.xml                          # Maven build
├── src/
│   └── main/
│       ├── java/com/studio/
│       │   ├── App.java             # Entry point
│       │   ├── model/               # Data models (7 classes)
│       │   ├── store/               # Filesystem persistence (3 classes)
│       │   ├── service/             # Business logic (7 classes)
│       │   ├── controller/          # FXML controllers (7 classes)
│       │   ├── runtime/             # Thread-local browser state
│       │   └── util/                # Utilities (3 classes)
│       └── resources/
│           ├── fxml/                # UI layouts (6 files)
│           └── css/studio.css       # Catppuccin Mocha theme
├── data/                            # Runtime data (user-configured)
│   ├── config/settings.json
│   ├── project/                     # Group/Suite/Case tree
│   ├── reports/                     # Run history + screenshots
│   └── exports/                     # Default Excel export location
├── work/                            # Temp (compile/harness)
├── scripts/                         # Build scripts (portable)
├── .github/workflows/               # CI/CD pipelines
└── docs/                            # Documentation
```

---

## Configuration

Application settings are stored in `data/config/settings.json`:

```json
{
  "browser": "CHROMIUM",
  "headless": false,
  "shareBrowserContext": true,
  "stopOnFirstFailure": false
}
```

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `browser` | string | `CHROMIUM` | Browser engine: `CHROMIUM`, `FIREFOX`, `WEBKIT`, `CHROME`, `EDGE` |
| `headless` | boolean | `false` | Run browsers without UI (Playwright headless mode) |
| `shareBrowserContext` | boolean | `true` | Share one BrowserContext across multi-case runs |
| `stopOnFirstFailure` | boolean | `false` | Halt the run after the first FAILED case |

The `APP_HOME` directory is resolved via (in priority order):

1. System property `-Dstudio.home=/path`
2. Environment variable `STUDIO_HOME`
3. Parent directory of the application JAR (portable mode)
4. Current working directory `user.dir` (dev mode)

---

## Documentation

| Document | Description |
|----------|-------------|
| [CHANGELOG.md](CHANGELOG.md) | Release history and changes |
| [docs/BUILD.md](docs/BUILD.md) | Build guide — dev and portable |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture, data flow, package structure |
| [docs/Playwright_Automation_Studio_Guide.md](docs/Playwright_Automation_Studio_Guide.md) | User guide — Hướng dẫn sử dụng (Vietnamese) |
| [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) | Release notes for v1.0.0 |

---

## License

[MIT](LICENSE)
