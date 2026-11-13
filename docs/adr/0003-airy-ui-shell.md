# ADR 0003 — Airy UI shell (2-pane + right drawer + responsive)

**Status**: Accepted  
**Date**: 2026-11-14  
**Spec**: `SPEC.md` (Phase 5.2 / Airy v4.1)  
**Design**: `docs/Thiết Kế Chi Tiết Giao Diện AI QA Test Studio.md`

## Context

Phase 4c used a fixed 3-column layout (Explorer | Editor | Browser+Copilot). The product design (Airy v4.1) requires a default **2-pane** canvas, Copilot/Browser as an on-demand **right slide-over**, thinner chrome, and desktop width breakpoints — without switching to FluentWindow.

## Decision

1. **Shell**: `QMainWindow` + qasync remains. Horizontal workspace = Sidebar + Editor; `RightDrawer` hosts Pivot `AI Copilot` | `Trình duyệt`.
2. **Drawer closed by default**; open via Copilot toggle / `Tạo bằng AI` / Run (Browser tab) / shortcuts.
3. **Breakpoints (window width)**: `≥1280` sidebar 260 / drawer ~400; `960–1279` sidebar 220 / drawer ~360; `<960` sidebar auto-collapse + drawer overlay; floor `800×600`; default open size `1200×720`.
4. **Copilot chat chrome**: one context row + thin Chat|Wizard pivot + empty **2×2 starters**; composer = input + Apply + Send; **Copy/Undo per message** (not chrome row). Agent Feed lives under Browser tab (≤5 lines).
5. **Tokens**: `bg.canvas/surface/elevated` + subtle borders in `theme.py` / `constants.py`.

## Consequences

- `BrowserAgentPane` no longer embeds Copilot Segmented; `MainWindow` wires through `RightDrawer`.
- Skill combo stays hidden on Top Bar (run still needs skill name); overflow `⋯` holds Export + Settings.
- Tests: `tests/ui/test_airy_shell.py` covers drawer default, toggle, starters, undo-from-message, overlay breakpoint.
