# Capability Map: AI QA Test Studio v4.0

Initiative rewrite from JavaFX Test Studio → Python/PySide6 MCP-powered QA Studio.
Source of truth: `docs/Bản Thiết Kế Tổng Thể AI QA Test Studio.md` + `SPEC.md`.

| Module id | Responsibility | Depends on |
|---|---|---|
| `foundation-scaffold` | Archive Java to `legacy/javafx/`, `uv`/`pyproject.toml` (package `ai_qa_test_studio`), lint/test gates | — |
| `core-models-storage` | Pydantic ISO 29119 + SharedElement; JSON/SQLite; Java `suite.json` importer + `legacy_locator_normalizer`; Settings/AI keys + TestDataProfile migrate | `foundation-scaffold` |
| `token-governance` | tiktoken budget, **in-process** Headroom + MarkItDown (`markitdown[docx,pdf,xlsx]`), sliding context (pure; no live Page) | `core-models-storage` |
| `mcp-host-skills` | MCP Host + `BinaryResolver` + `mcp_servers.json` (stdio subset); Progressive Skill Loader + 4 skills; stub MCP for tests | `foundation-scaffold` |
| `browser-engine` | Single Playwright context + ephemeral CDP (`127.0.0.1`); `DOMDistiller`; locator resolver; screencast/thumbnail APIs | `core-models-storage`, `token-governance` |
| `agent-runtime` | Spec generator, ShiftLeft Auto-Binder, Dual-Loop, Healer (CDP-shared browser) | `mcp-host-skills`, `browser-engine`, `token-governance` |
| `ui-workspace` | PySide6 + qasync; CDP screencast viewport (no QtWebEngine); Wizard; 4-mode editor (drawer = pane 4) | `agent-runtime`, `core-models-storage`, `browser-engine` |
| `reporting-packaging` | HTML Visual Replay, IEEE 829 Excel/RTM, pytest+CI export, PyInstaller | `core-models-storage`, `agent-runtime` |

**Build order:** `foundation-scaffold` → `core-models-storage` + `mcp-host-skills` → `token-governance` → `browser-engine` → `agent-runtime` → (`ui-workspace` ∥ `reporting-packaging`).

**Hard rule:** `reporting-packaging` must **not** depend on `ui-workspace` (headless CI / no X11).

Module specs (when split later): `SPEC-<module-id>.md`. Until then, binding requirements live in root `SPEC.md`.
