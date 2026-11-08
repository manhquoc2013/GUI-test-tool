# Changelog

All notable changes to Playwright Automation Studio are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.0.0] — 2026-11-08

### Added

- **Full Modern Pro IDE Workspace**:
  - Three-level tree hierarchy (**Group → Suite → Case**) with drag-and-drop reordering.
  - Multi-tab document workspace (`TabManager`) for editing scripts, viewing DOM, and configuring assertions.
  - Inline hover action buttons for instant run, edit, record, and delete.
  - Catppuccin Mocha dark theme and vector SVG icons (`UiIcons`).
- **Autonomous AI Assistant & Test Generator**:
  - Natural Language test case generation with dual representation (NLP steps + test data matrix + Playwright Java code).
  - Vietnamese AI Skill Suite with auto-routing (`/testplan`, `/edgecase`, `/data`, `/healing`, `/summary`).
  - `LiveDomScanner` with in-memory LRU cache with TTL for remote and active browser inspection.
  - Vision/Multimodal element analysis for complex layouts.
- **5-Tier Self-Healing Runtime Engine**:
  - Dynamic Proxy `Locator` wrapping with deep chaining support (`.first()`, `.last()`, `.nth()`, `.filter()`).
  - Rule-based Heuristic repair (<30ms, zero cost) for dynamic IDs, text shifts, and ARIA roles.
  - VLM Multimodal fallback for visual element recovery.
  - Automatic mutation persistence into Java source files (`writeTextAtomic`) and `suite.json`.
- **4 Run Scopes & In-Process Compilation**:
  - Run All, Run Group, Run Suite, and Run Case via `javax.tools.JavaCompiler` and `URLClassLoader`.
  - Shared browser session across cases with persisted auth cookies and storage.
  - Non-blocking hard-cancel coordination (`RunCancelService`).
- **Assertion Builder with 46 Assertion Types**:
  - 8 categories: Page Content, Elements/UI, Media, Download, Tables, Forms, State, and Custom JS.
  - Lifecycle states: DRAFT, ACTIVE, INACTIVE with soft-assert scoring.
- **High-Performance Embedded Database (H2)**:
  - Dual-persistence architecture: human-readable disk JSON + H2 in-memory/file cache.
  - Recursive project synchronization for nested groups.
  - Full-text search and batch assertion query loading (eliminating N+1 overhead).
- **Enterprise Reporting & Bilingual Export**:
  - ISTQB / IEEE 829-compliant 3-sheet Excel report and Test Case documentation export.
  - Single-file standalone HTML report with dark theme styling.
  - Visual screenshot evidence viewer with zoom dialog.
  - Bilingual export support (Tiếng Việt default / English).
- **Security & Data Protection**:
  - Transparent AES-256-GCM encryption at rest for API keys with physical machine-bound entropy.
  - Automated redaction of secrets in terminal logs, toasts, and exception messages (`SecuritySanitizer`).
  - Active-run guard preventing accidental deletion during test runs.
- **OS Resource Reclamation**:
  - Automatic uncommit of heap memory back to OS upon minimization or idle.
  - Graceful shutdown lifecycle killing all background Playwright and Node processes.
- **Multi-Platform Offline Installers**:
  - Native macOS `.dmg` drag-and-drop installer with bundled JRE 26, Node.js, and offline Chromium.
  - Native Windows Setup `.exe` wizard installer (Inno Setup) with desktop shortcuts and uninstaller.
  - GitHub Actions CI/CD matrix workflows for automated builds across Ubuntu, macOS, and Windows.

### Fixed

- 36 bugs and issues resolved across 7 fix iterations
- Assert Builder FXML loading and controller binding
- ProjectStore path resolution and file deletion
- Screenshot injection logic in harness transformer
- SharedBrowserSession resource cleanup (Playwright, Browser, BrowserContext lifecycle)
- Cross-platform BrowserDetector paths for macOS, Windows, and Linux
- Terminal auto-show on Record/Run triggers
- Browser combo preference restoration from settings
- Code deduplication — `StringEscapeUtil` unified, `resolveSuitePath` centralized, shared `ObjectMapper` instance
- Status constants for type safety (string enum replaced with constants)
- Safe delete with confirmation dialog showing affected child count
- Run scope naming and scopePath consistency in `RunReport`
- Assertion injection brace-matching for `main()` method boundaries
- Editor content bindings in workspace for code save
