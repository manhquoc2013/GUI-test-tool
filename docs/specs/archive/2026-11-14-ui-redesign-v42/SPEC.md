# Spec: UI Redesign v4.2 — 3-Column Full Cutover

**Status**: ACTIVE  
**Product**: AI QA Test Studio v4.2  
**Date**: 2026-11-14  
**Design**: resolve via glob `docs/*AI QA Test Studio*.md` (redesign doc)  
**Supersedes**: ADR 0003 Airy 2-pane + slide-over (see ADR 0004)

## Objective

Replace the Airy v4.1 2-pane + geometry overlay drawer with a persistent 3-column `QSplitter` shell (Explorer | Workspace | RightDock), apply design tokens/breakpoints (1440 / 1150 / 1240), and ship the six UX fixes (Wizard scroll, Unbound banner, FlowLayout pills, Breadcrumb+ElidedTitle, BottomDataDrawer 42px, Settings Master-Detail) while keeping `QMainWindow`, controller wiring, and `RightDrawer` public API stable.

**Success**: `uv run ruff check src tests` · `uv run mypy src` · `uv run pytest` green; shell tests assert 3-column + mutual exclusion; no overlay/margin push model.

## Decisions locked

| Area | Decision |
|---|---|
| Shell | 3-column `QSplitter`; not ADR 0003 slide-over |
| Window | Keep `QMainWindow` (no FluentWindow) |
| Package | Stay under `ui/widgets/`; add `badges.py` |
| API | Keep `RightDrawer` `open_drawer` / `close_drawer` / `is_open` / `show_copilot` / `show_browser` |
| Editor min | `UI_EDITOR_MIN_WIDTH = 520` (H1) |
| Mutual exclusion | `UI_THREE_COL_SAFE = 1240` |
| Drawer ownership | `TestDataDrawer.toggled(bool)` → EditorPane owns `v_splitter` sizes (EX1) |
| Git | One atomic commit per completed slice |

## Hardening (binding)

- **H1–H5**, **SG1–SG3**, **EX1–EX3** as in initiative plan (saved widths ignore 0; breadcrumb `blockSignals`; theme disconnect-on-destroy; design-doc glob; `QWIDGETSIZE_MAX` from `QtWidgets`).
- Layout mockups **M0–M8** are the visual contract.

## Boundaries

- Out: FluentWindow, package move to `docks/`, agent/MCP semantics, shipping preview script.
- Settings: reshape shell only; preserve `objectName` / public attrs for keyring tests.

## Verification

- Gates: ruff / mypy / pytest — 0 errors.
- Tests: `tests/ui/test_shell_v42.py` replaces overlay-era `test_airy_shell.py`.
