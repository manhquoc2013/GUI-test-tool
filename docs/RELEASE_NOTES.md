# Release Notes — v1.0.0

## Playwright Automation Studio v1.0.0

**Release date:** 2026-07-25

First production release of **Playwright Automation Studio** — a JavaFX desktop application for managing and running Playwright test automation without writing code manually.

---

### What's New

#### Visual Test Tree Management

Organize tests in a three-level hierarchy: **Group → Suite → Case**. Full CRUD with rename, delete, and duplicate. Drag-and-drop reorder within the Explorer tree and Assertion Builder.

#### Record & Replay

Record browser interactions via Playwright codegen in your chosen browser (Chromium, Firefox, WebKit, Chrome, Edge). Close the browser when done — the **Assertion Builder** opens automatically so you can add pass/fail conditions immediately.

#### 46 Assertion Types

A comprehensive catalog of assertions across 8 categories, all accessible through a visual builder:

| Category | Types |
|----------|-------|
| **Page Content** | TEXT_VISIBLE, TEXT_NOT_VISIBLE, TEXT_EXACT, HEADING_VISIBLE, TITLE_CONTAINS/EQUALS, URL_CONTAINS/EQUALS/MATCHES, PAGE_HAS_TEXT |
| **Elements / UI** | ELEMENT_VISIBLE/HIDDEN/ENABLED/DISABLED/EDITABLE/CHECKED/UNCHECKED, ELEMENT_COUNT, ELEMENT_HAS_TEXT/VALUE, ATTRIBUTE_EQUALS, CSS_CONTAINS, LINK_HREF_CONTAINS |
| **Images / Media** | IMAGE_VISIBLE, IMAGE_COUNT, VIDEO_VISIBLE, AUDIO_VISIBLE |
| **File / Download** | FILE_DOWNLOADED, FILE_NAMED, FILE_EXTENSION |
| **Tables** | TABLE_HAS_TEXT, TABLE_ROW_COUNT, TABLE_CELL_EQUALS, TABLE_HEADER_CONTAINS |
| **Forms** | INPUT_VALUE_EQUALS/CONTAINS, PLACEHOLDER_EQUALS, SELECT_HAS_OPTION, FORM_VALID/INVALID |
| **Page / Network State** | NO_CONSOLE_ERROR, COOKIE_EXISTS, LOCAL_STORAGE_EQUALS, SESSION_STORAGE_EQUALS |
| **Custom** | CUSTOM (locator + expect operator), CUSTOM_JS (arbitrary JavaScript) |

Each assertion has a status: **DRAFT** (not ready), **ACTIVE** (will execute), **INACTIVE** (temporarily disabled). Only ACTIVE assertions run.

#### 4 Run Scopes

- **Run All** — Every eligible case in the project (suites must be ACTIVE, cases must be Ready with active assertions).
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

- **ci.yml** — Build verification on every PR and push to `main`.
- **release-portable.yml** — Matrix build (macOS, Windows, Linux) on tag, producing portable `.zip` artifacts.

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
