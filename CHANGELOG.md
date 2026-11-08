# Changelog

All notable changes to Test Studio are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [2.2.0] — 2026-11-08

### Added

- **Optimized Self-Healing Execution Engine**:
  - `OptimizedSelfHealingExecutor`: fast-path locator execution bypassing overhead when initial selector is healthy, multi-strategy heuristic recovery (<30ms) for dynamic ID patterns, normalized text, ARIA roles, and attribute fuzzy matching.
  - `HealedLocatorWrapper`: transparent Playwright Locator wrapper with automatic runtime healing and event listener notifications.
  - Self-healing strategy cache with TTL and hit rate monitoring.
- **AI Assistant Dialog & Test Generation Service**:
  - `AiCasePromptDialog`: modern JavaFX modal for prompt-based test creation with context customization and AI specialist selection.
  - Multi-tier resilient AI response parser: Tier 1 (standard JSON), Tier 2 (fenced Markdown code block), and Tier 3 (regex repair) ensuring zero failure on varied LLM responses.
  - Automated generation of Playwright Java test code, NLP steps, and test data schemas.
- **Assertion Models & Robust Runtime Assertions**:
  - `Assertion` model with support for 46 assertion types, active state management (DRAFT, ACTIVE, INACTIVE), targets, and comparators.
  - `StudioAssertions` and `StudioRuntime` layer for in-process Playwright test execution with soft assertions and error diagnosis.
  - `AssertCodeGenerator` for generating clean, idiomatic Playwright Java assertion code.
  - Test runner reconciliation linking runtime assertion evaluations with test case reports and evidence.

---

## [2.1.0] — 2026-11-08

### Added

- **In-App User Guide Viewer**:
  - Full user guide viewer integrated directly into Help Drawer (`UserGuideService`).
  - Bilingual support (Vietnamese & English) with instant chapter navigation and embedded theme-responsive screenshots.
  - Offline packaging of guides and assets inside JAR.
- **MarkItDown Hybrid Document Ingestion**:
  - Multi-format document parser supporting PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and HTML into normalized Markdown for AI context.
- **Agency-Agents Specialist Roster**:
  - Specialist prompts and system configurations (QA Architect, Security Specialist, Edge-case Analyst, Test Data Engineer).
- **Structured Test Error Reporting**:
  - Categorized error diagnosis (`LocatorNotFound`, `Timeout`, `AssertionFailure`, `NavigationError`, `NetworkError`) replacing raw stack traces.
  - Automatic reproduction step generation in HTML and Excel reports.

### Fixed

- Modal dialog stretching and height constraints (`ExportLanguageDialog`, `MarkdownPreviewDialog`).
- Theme variable `ClassCastException` for `-fx-border-color` during dark/light theme switching.
- Process destruction lifecycle and thread affinity safeguards.
- H2 connection pooling and AST parsing robustness.

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
