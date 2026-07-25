# Changelog

All notable changes to Playwright Automation Studio are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.0.0] — 2026-07-25

### Added

- Full Playwright Automation Studio desktop application
- 4 run scopes: **Run All**, **Run Group**, **Run Suite**, **Run Case**
- Shared browser context for multi-case runs (Suite / Group / All)
- Per-case isolated context (Run Case single)
- Assertion Builder with **46 assertion types** across 8 categories:
  - Page content (TEXT_VISIBLE, TEXT_NOT_VISIBLE, TEXT_EXACT, HEADING_VISIBLE, TITLE_CONTAINS/EQUALS, URL_CONTAINS/EQUALS/MATCHES, PAGE_HAS_TEXT)
  - Elements/UI (ELEMENT_VISIBLE/HIDDEN/ENABLED/DISABLED/EDITABLE/CHECKED/UNCHECKED, ELEMENT_COUNT, ELEMENT_HAS_TEXT/VALUE, ATTRIBUTE_EQUALS, CSS_CONTAINS, LINK_HREF_CONTAINS)
  - Images/Media (IMAGE_VISIBLE, IMAGE_COUNT, VIDEO_VISIBLE, AUDIO_VISIBLE)
  - File/Download (FILE_DOWNLOADED, FILE_NAMED, FILE_EXTENSION)
  - Tables (TABLE_HAS_TEXT, TABLE_ROW_COUNT, TABLE_CELL_EQUALS, TABLE_HEADER_CONTAINS)
  - Forms (INPUT_VALUE_EQUALS/CONTAINS, PLACEHOLDER_EQUALS, SELECT_HAS_OPTION, FORM_VALID/INVALID)
  - Page/Network state (NO_CONSOLE_ERROR, COOKIE_EXISTS, LOCAL_STORAGE_EQUALS, SESSION_STORAGE_EQUALS)
  - Custom (CUSTOM, CUSTOM_JS)
- Assert status lifecycle: DRAFT → ACTIVE → INACTIVE
- Record & replay via Playwright codegen with automatic Assertion Builder popup after recording
- Soft-assert injection with pass/fail/skip tracking per assertion
- Assert-results file per case for detailed failure analysis
- Screenshot capture on test completion — screenshots saved for both PASSED and FAILED cases
- Toast notification system (SUCCESS / INFO / WARN / ERROR) for every user action
- Cross-platform browser detection — Playwright-bundled Chromium, Firefox, WebKit + system Chrome and Edge channels
- Browser selection combo on Workspace with preference persistence
- Collapsible sidebar (expanded 220px / collapsed 44px)
- Auto-show terminal on Record and Run
- Drag-and-drop reorder in Explorer tree (Groups, Suites, Cases) and Assertion Builder (assertions)
- Suite Detail / Preview view with run eligibility per case
- Excel report export — 3 sheets: Summary, Cases, Asserts
- Report filtering by suite name, result (All / Passed / Failed), and scope
- Report persistence to `data/reports/` with screenshots
- Project tree filesystem mirror in `data/project/` — `group.json` per group, `suite.json` per suite, `.java` scripts per case
- CRUD + Copy/Duplicate for Suites, Cases, and Assertions
- Suite and Case status management (Suite: DRAFT/ACTIVE/INACTIVE; Case: Draft/Ready)
- Stop-on-first-failure toggle (default: false)
- User settings persistence in `data/config/settings.json`
- `APP_HOME` resolution chain: `studio.home` property → `STUDIO_HOME` env → JAR location → `user.dir`
- Portable build scripts (macOS/Linux + Windows) — Node.js 20.18.0 LTS + Playwright CLI + Chromium bundled
- GitHub Actions CI/CD — build verification on PR/push (`ci.yml`), matrix portable release on tag (`release-portable.yml`)
- Catppuccin Mocha dark theme (`studio.css`)
- FXML-based UI — 6 layouts with dedicated controllers

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
