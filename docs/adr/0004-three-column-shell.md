# ADR 0004 — Three-column responsive shell (v4.2)

**Status**: Accepted  
**Date**: 2026-11-14  
**Spec**: `docs/specs/2026-11-14-ui-redesign-v42/SPEC.md`  
**Design**: glob `docs/*AI QA Test Studio*.md` (UI redesign)  
**Supersedes**: [ADR 0003 — Airy UI shell](0003-airy-ui-shell.md)

## Context

ADR 0003 used a 2-pane canvas with a right slide-over drawer (push ≥960 / overlay &lt;960). The redesign specification requires a persistent 3-column layout, new breakpoints (1440 / 1150), mutual exclusion under 1240px, and six UX remediations that the overlay model fights (min-width sum, saved column widths, theme-aware surfaces).

## Decision

1. **Shell**: `QMainWindow` + horizontal `QSplitter` = Explorer | Editor | RightDock (`RightDrawer` as third splitter child via `setVisible`, not `setGeometry` overlay).
2. **Breakpoints**: Wide ≥1440 (3 columns); Medium ≥1150 (open dock auto-hides Explorer); Compact &lt;1150 (TopBar label compaction); Safe &lt;1240 (mutual exclusion Explorer ↔ Dock).
3. **Editor floor**: `UI_EDITOR_MIN_WIDTH = 520` so three-column min sum stays under 1150.
4. **Saved widths**: persist on `splitterMoved` only when pane visible and size ≥ min (SG1).
5. **Data drawer**: widget emits `toggled(bool)`; EditorPane owns vertical splitter sizes (EX1).
6. **Theme**: Dark/Light rgba elevation helpers; `qconfig.themeChanged` with disconnect-on-destroy (SG3/EX2).
7. **API**: Keep `RightDrawer` public open/close/show methods for controllers.

## Consequences

- ADR 0003 push/overlay tests and layout paths are retired.
- `tests/ui/test_shell_v42.py` is the shell regression gate.
- Controllers (`Layout`/`Run`/`Copilot`/`Export`) remain; only layout mechanics change.
