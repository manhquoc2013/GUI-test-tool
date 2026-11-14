# Spec: Copilot History Overlay + Parallel Remediation

**Status**: COMPLETE  
**Product**: AI QA Test Studio v4.2  
**Date**: 2026-11-14  
**Extends**: archived `copilot-history-parallel`  
**Interview**: Q1=overlay drawer; Q2=layout + Must + Should

## Objective

Make Copilot usable in a narrow dock: history as a **slide-over overlay** (not side-by-side), and close review gaps so parallel runs stay correct (Stop isolation, live bubble paint, ingest/clarify safety).

**Success**: Chat stays full-width; History toggle overlays a drawer; Stop never cancels a background session; switch-back mid-role paints the bubble; gates green.

## Locked decisions

| Area | Decision |
|---|---|
| History UI | Overlay drawer over chat (not HBox rail, not popover) |
| Scope | Layout + Must (cancel/bubble) + Should (ingest, clarify-by-session) |

## Commands

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

## Structure

- `ui/widgets/chat_pane.py` — overlay host; full-width chat
- `ui/widgets/chat_history_rail.py` — overlay-capable width/chrome
- `ui/controllers/copilot_controller.py` — cancel/clarify/ingest fixes
- `ui/main_window.py` — shutdown cancel-all
- `constants.py` — overlay width / dimmer tokens as needed
- tests: parallel stop isolation, end_role repaint, overlay layout

## Boundaries

**In:** overlay layout; stale-cancel fix; bubble repaint; ingest when not viewing; clarify keyed by session.  
**Out:** multi-pane chat; redesign of message bubbles/roles.

## Verification

- [x] History open → drawer covers chat; chat column not squeezed
- [x] History closed → chat full width
- [x] Stop on B does not set A’s cancel Event
- [x] Shutdown cancels all running sessions
- [x] Switch-back mid-role → end_role updates visible bubble
- [x] ruff / mypy / pytest green (290 passed)

