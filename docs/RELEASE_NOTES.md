# Release Notes — v1.0.0

## Playwright Automation Studio v1.0.0

**Release date:** 2026-11-08

First production release of **Playwright Automation Studio** — an enterprise-ready JavaFX desktop application for visual, AI-assisted, and self-healing Playwright test automation with offline installer packaging.

---

### Key Capabilities in v1.0.0

#### 1. Visual Test Tree & Modern IDE Workspace
- **Three-Level Tree Management**: Group → Suite → Case hierarchy with drag-and-drop reordering, copy/duplicate, and live state filters.
- **Multi-Tab Document Workspace**: `TabManager` supporting simultaneous case script editing, DOM inspection, and assertion building.
- **Inline Hover Action Buttons**: Quick-run, edit, and deletion directly on tree items with smooth animations.
- **Dual-Theme Catppuccin Mocha**: Refined dark theme with vector SVG icons (`UiIcons`).

#### 2. Autonomous AI Assistant & Test Generator
- **Multi-Stage Generation**: Natural language prompts produce dual representation (NLP steps + test data matrix + Playwright Java code).
- **Vietnamese AI Skill Suite**: Built-in specialized skills (`/testplan`, `/edgecase`, `/data`, `/healing`, `/summary`) with automatic heuristic routing.
- **Live DOM Scanner**: In-memory LRU cache with TTL for rapid interactive page element extraction.
- **Vision/Multimodal Inspection**: High-fidelity visual analysis for complex web applications.

#### 3. 5-Tier Self-Healing Runtime Engine
- **Zero-Latency Heuristics (<30ms)**: Intelligent automatic recovery from dynamic IDs, text shifts, and ARIA roles.
- **Dynamic Proxy Locator**: Seamless chaining (`.first()`, `.last()`, `.nth()`, `.filter()`) preserving native auto-waiting.
- **VLM Vision Fallback**: Multimodal visual recovery for altered UI components.
- **Atomic Mutation Persistence**: Automatically updates Java source files and assertion metadata without file corruption risks.

#### 4. High-Speed Embedded Database (H2) & Dual Persistence
- **Dual-Layer Cache**: Durable JSON on disk paired with embedded H2 database for sub-millisecond FTS search and analytics.
- **Recursive Directory Sync**: Automatically discovers and indexes deeply nested groups and suites.
- **Batch Querying**: Eliminates N+1 query latency when loading test suites.

#### 5. Enterprise Reporting & Bilingual Export
- **ISTQB / IEEE 829 Compliant Excel Exports**: 3-sheet execution reports and comprehensive test case documentation.
- **Standalone HTML Report**: Self-contained single-file HTML reports styled with Catppuccin dark theme.
- **Screenshot Evidence Viewer**: Full-screen modal evidence viewer with zoom and path resolution.
- **Bilingual Export Selection**: One-click language switching between Tiếng Việt (default) and English.

#### 6. Enterprise Security & Resource Reclamation
- **AES-256-GCM Key Encryption**: Transparent encryption at rest bound to physical hardware entropy with dual-key fallback.
- **Continuous Secret Sanitization**: Redacts tokens from terminal logs, exceptions, and toasts.
- **Automatic Memory Reclamation**: Uncommits unused heap memory to the OS on window minimization or idle.

#### 7. Multi-Platform Offline Installers
- **macOS Drag-and-Drop `.dmg`**: Full offline package with bundled JRE 26, Node.js 20, and offline Chromium.
- **Windows Setup `.exe`**: Professional Inno Setup installer wizard with desktop shortcuts and uninstaller.
- **GitHub Actions CI/CD**: Automated multi-OS releases across macOS, Windows, and Ubuntu.
- **Run Group** — Recursively run all eligible cases within a group.
- **Run Suite** — Run all eligible cases in a single suite (allows DRAFT/INACTIVE suites for debugging).
- **Run Case** — Run a single case.

#### Shared Browser Context

Multi-case runs (Suite / Group / All) share a **single BrowserContext** by default — cookies, localStorage, and login sessions persist across cases. No need to re-login for each test. Toggle off for isolated execution.

#### Soft-Assert with Detailed Reporting

Assertions use a **soft-assert** pattern — all active assertions execute even if earlier ones fail. Each case's report shows exactly which assertions passed, which failed, and why any were skipped. The run summary displays as `xP / yF / zS` (Passed / Failed / Skipped).

#### Screenshot Capture

Every executed case (both PASSED and FAILED) captures a screenshot. View screenshots inline in the **Report Detail** view or open them with your system's default image viewer.

#### Excel Export

Export any run report to Excel with **3 sheets**:

| Sheet | Contents |
|-------|----------|
| **Summary** | Scope, browser, duration, pass/fail/skip counts |
| **Cases** | Per-case status, duration, skip reason, screenshot path |
| **Asserts** | Per-assertion config status, result status, failure message |

#### Toast Notifications

Every user action produces immediate, non-blocking feedback via toasts at the top-right of the shell window — green for success, gray for info, orange for warnings, red for errors.

#### Portable Distribution

A self-contained bundle that runs on any machine **without installing JDK, Node.js, or Maven**. Just extract and double-click. The portable build includes:

- Embedded JRE with JavaFX modules
- Node.js 20.18.0 LTS (for codegen)
- Playwright CLI (local npm install)
- Chromium browser (PLAYWRIGHT_BROWSERS_PATH preset)

#### Dark Theme

The entire UI uses the [Catppuccin Mocha](https://github.com/catppuccin/catppuccin) dark theme for comfortable extended use.

#### CI/CD

GitHub Actions pipelines:

- **ci.yml** — Build verification on version tags (`v*`) only.
- **release-portable.yml** — Matrix portable build (macOS, Windows, Linux) on the same tags, producing `.zip` artifacts.

---

### System Requirements

#### Development Mode

| Requirement | Version |
|-------------|---------|
| JDK (Temurin recommended) | 26 |
| Maven | 3.9+ |
| (Optional) Node.js | 20+ |

#### Portable Mode

**No prerequisites.** Extract the `.zip` and run the launcher.

---

### Known Limitations (v1)

- **No embedded browser** — Playwright's browser runs as a separate process; there is no JavaFX-embedded WebView for Playwright.
- **No video recording** — Screenshots are captured, but video of test runs is not supported.
- **No email report notifications** — Reports are viewable in-app or exportable to Excel; no SMTP integration.
- **No concurrent multi-user access** — The application is single-user, single-instance.
- **No AI-powered test generation** — Tests are created manually (New Case) or via recording (Playwright codegen). No LLM-based step generation.
- **No many-to-many case sharing** — Each test case belongs to exactly one suite. Use Copy/Duplicate to reuse in other suites.
- **No E2E GUI testing on CI** — The CI pipeline verifies compilation and packages portable builds but does not run the application's GUI tests.
- **Assertions are suite-scoped** — Assertions are defined at the suite level, not per-case (all cases in a suite share the same assertion set).

---

### Upgrade Notes

This is the **first release** — no upgrade path is needed.

If migrating from manual Playwright Java scripts:

1. Organize scripts into the `data/project/` tree (create `group.json` and `suite.json` files).
2. Suite assertions are defined in `suite.json` and automatically injected at runtime.
3. Scripts that call `Playwright.create()` / `browser.close()` will be automatically transformed by the harness — no manual edits required for the shared-context path.
