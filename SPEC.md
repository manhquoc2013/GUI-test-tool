# Spec: AI QA Test Studio v4.1 — Copilot Crew + Airy UI

**Status**: ACTIVE  
**Product**: AI QA Test Studio v4.1 (Python / PySide6)  
**ADRs**: `docs/adr/0001-…`, `0002-…`, `0003-airy-ui-shell.md`  
**UI design SoT**: `docs/Thiết Kế Chi Tiết Giao Diện AI QA Test Studio.md` (Airy v4.1)

## Objective

Ship an MCP-powered Shift-Left QA desktop studio with:

1. **Crew Copilot** (Phase 5.1): Planner→Composer→Critic, hybrid summaries, clarify popup, bundled MCP, Settings hub, task×complexity ModelRouter.  
2. **Airy shell** (Phase 5.2): 2-pane default + right drawer (Copilot | Browser), responsive width breakpoints, redesigned chat chrome.

**Success**: All cognitive steps via `UnifiedLLMGateway`; drawer closed on startup; gates green (`ruff` / `mypy` / `pytest`).

## Decisions locked

| Area | Decision |
|---|---|
| Window | `QMainWindow` + qasync — **not** FluentWindow |
| Layout | Default Sidebar + Editor; RightDrawer on demand |
| Drawer | Closed at startup; Copilot \| Browser pivot |
| Size | Min `800×600`; default `1200×720` |
| Breakpoints | Width `≥1280` / `960–1279` / `800–959` (overlay) |
| Chat UI | Context row + Chat\|Wizard; starters 2×2; Copy/Undo per bubble |
| Feed | Compact agent feed under Browser tab (≤5 lines) |
| Crew | Summaries ≤160 chars; debate ≤3; clarify any role |
| MCP | In-process `qa-compress` / `qa-crew-memory` / `qa-project-context` |
| Settings | System Settings hub — multi AI API + multi models + routing |
| Router | Auto `(task, complexity)→tier→model`; manual pin overrides |

## Architecture (UI)

```text
MainWindow
  UnifiedTopBar (hamburger · Tạo bằng AI · URL · Sync(N) · Chạy · Copilot · ⋯)
  IndeterminateProgressBar (Sync/Run busy)
  workspace
    h_splitter: ExplorerPane | EditorPane
    RightDrawer (geometry overlay / push): CopilotChatPane | BrowserAgentPane
  TestDataDrawer (bottom)
  StatusBar (MCP / Skills / Tokens)
```

Shortcuts: `Ctrl+K` palette · `Ctrl+\` sidebar · `Ctrl+I` Copilot drawer · `Ctrl+Enter` Run · `Esc` close drawer / Stop.

## Pipeline (Copilot)

```text
User → classify complexity → Planner (LLM)
     → optional clarify → Composer → Critic
     → bounded debate (≤3) → optional revise → complete
```

Events: `status_summary`, `agent_message`, `clarify_request`, `clarify_resolved`, `loop_halted`, `apply_ready`, `stopped`, `pipeline_complete`.

## Key modules

```text
agent/     orchestrator, model_router, complexity_classifier, bundled_mcp, llm_gateway
core/      models, storage, env_manager, chat_models/history
ui/        main_window, theme, widgets/{right_drawer,chat_pane,top_toolbar,…}
reporting/ html / excel / pytest exporters (no PySide6 import)
```

## Testing / Gates

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

UI smoke: `tests/ui/test_main_window_smoke.py`, `tests/ui/test_airy_shell.py`.

## Boundaries

**In**: Crew UX, clarify, bundled MCP, Settings, router, Airy shell/chat.  
**Out**: Unlimited swarm; Dual-Loop rewrite; FluentWindow; light-theme polish; web/mobile UI.
