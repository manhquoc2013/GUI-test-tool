# Spec: Copilot Chat History + Parallel Runs

**Status**: ACTIVE  
**Product**: AI QA Test Studio v4.2  
**Date**: 2026-11-14  
**Capability map**: `CAPABILITY_MAP.md`  
**Reference**: JavaFX `AiChatHistoryStore` + `AiChatRunRegistry` (parity intent; prefer repo Python evidence)

## Objective

Restore discoverable chat history and allow multiple Copilot conversations to generate in parallel.

**User**: QA engineer in AI Copilot (Chat mode).  
**Success**: Session list + full transcript restore; start/run chat B while chat A still generates; history badges show running/unread; Stop only affects the viewed session.

## Locked decisions (from interview)

| Q | Decision |
|---|---|
| History | Both: session list **and** transcript restore |
| Parallel | Parallel **runs** (not multi-pane) — one transcript pane; switch keeps agents alive |

## Assumptions (proceeding)

1. Collapsible history **rail** inside Copilot (JavaFX-style), toggled from toolbar — not a separate window.
2. Persistence stays workspace-wide via existing `ChatHistoryStore` → `data/.../copilot/sessions.json`.
3. Empty sessions (no user messages) are omitted from the rail / pruned on leave.
4. No hard concurrent-run cap (registry map only).
5. On startup: load most recently updated non-empty session into the pane; else blank “Phiên mới”.
6. Theme only via `ThemeTokens` + `bind_widget_theme`.
7. Do not stage a fat `sessions.json` tip (fixture stays lean).

## Commands

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
```

## Project structure (touched)

- `src/ai_qa_test_studio/core/chat_run_registry.py` — new
- `src/ai_qa_test_studio/core/chat_history.py` — prune helpers if needed
- `src/ai_qa_test_studio/ui/widgets/chat_history_rail.py` — new
- `src/ai_qa_test_studio/ui/widgets/chat_pane.py` — rail host, history toggle, per-view busy
- `src/ai_qa_test_studio/ui/controllers/copilot_controller.py` — registry-aware runs + switch
- `src/ai_qa_test_studio/ui/main_window.py` — wire store restore / registry
- `src/ai_qa_test_studio/constants.py` — labels, widths, badge strings
- `tests/unit/test_chat_run_registry.py`, `tests/unit/test_chat_history*.py`, UI tests as needed

## Code style

- Existing PySide6 / pydantic patterns; no unused imports; no magic UI strings (constants).
- Tree mutations unchanged (`project_tree_ops` out of scope).

## Testing strategy

- Unit: registry start/cancel/finish/unread/live session; store list/prune/load.
- Unit/UI: rail selects session → `load_session`; new/delete refresh list.
- Unit: switching viewed session mid-run does not cancel other session’s Event; finish-off-view marks unread.
- Gates: ruff / mypy / pytest — 0 errors.

## Boundaries

**In**

- History rail UI + badges (running / unread).
- Transcript restore on select and app start.
- Parallel asyncio runs keyed by `session.id`.
- Per-viewed-session busy/stop; background runs keep writing into their live `CopilotSession`.

**Out**

- Side-by-side multi-pane chat.
- Sub-agent session tree UI.
- Migrating JavaFX `.ai_chat.json` files.
- Changing orchestrator crew semantics / MCP.

## Verification checklist

- [ ] Open Copilot → history toggle shows prior sessions with titles.
- [ ] Select session → transcript restores messages.
- [ ] Restart app → last non-empty session transcript restored.
- [ ] Run A, new session B, run B while A still working → both complete; A shows running badge then unread if not viewed.
- [ ] Stop on B does not cancel A.
- [ ] Gates green.
