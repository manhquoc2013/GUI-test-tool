# Capability Map: AI QA Test Studio v4.1

Initiative rewrite from JavaFX Test Studio → Python/PySide6 MCP-powered QA Studio.  
Source of truth: `docs/Bản Thiết Kế Tổng Thể AI QA Test Studio.md` + `SPEC.md` + Airy design doc.

| Module id | Responsibility | Depends on |
|---|---|---|
| `foundation-scaffold` | Archive Java to `docs/legacy/javafx/`; `uv`/`pyproject.toml` (package `ai_qa_test_studio`); lint/test gates | — |
| `core-models-storage` | Pydantic ISO 29119 + SharedElement; JSON storage; Java importer; Settings/AI connections + TestData migrate | `foundation-scaffold` |
| `token-governance` | tiktoken budget, in-process Headroom + MarkItDown; sliding chat context | `core-models-storage` |
| `mcp-host-skills` | MCP Host + BinaryResolver; Progressive Skill Loader; **bundled** in-process MCP (`qa-*`) | `foundation-scaffold` |
| `browser-engine` | Single Playwright context + ephemeral CDP; DOMDistiller; screencast APIs | `core-models-storage`, `token-governance` |
| `agent-runtime` | Spec generator, Auto-Binder, Dual-Loop, Healer (shared CDP browser) | `mcp-host-skills`, `browser-engine`, `token-governance` |
| `qa-copilot` | CopilotChatPane (Airy); crew orchestrator; ModelRouter; ClarifyDialog; chat history/stop/per-msg copy-undo | `agent-runtime`, `token-governance`, `ui-workspace`, `core-models-storage` |
| `ui-workspace` | Airy `QMainWindow`: 2-pane + `RightDrawer` (Copilot\|Browser); TopBar; Explorer Pivot; Editor canvas; width breakpoints | `agent-runtime`, `core-models-storage`, `browser-engine` |
| `reporting-packaging` | HTML Visual Replay, IEEE 829 Excel/RTM, pytest+CI export, PyInstaller | `core-models-storage`, `agent-runtime` |

**Build order:** `foundation-scaffold` → `core-models-storage` + `mcp-host-skills` → `token-governance` → `browser-engine` → `agent-runtime` → (`ui-workspace` ∥ `reporting-packaging`) → `qa-copilot`.

**Orchestrator (Phase 5.1):** Planner → Composer → Critic + hybrid debate/clarify — ADR `0001` + `0002`.  
**Shell (Phase 5.2 / Airy):** ADR `0003` — drawer closed by default; Copilot not permanently in a third column.

**Hard rule:** `reporting-packaging` must **not** depend on `ui-workspace` (headless CI / no X11).

Module specs (when split later): `SPEC-<module-id>.md`. Until then, binding requirements live in root `SPEC.md`.
