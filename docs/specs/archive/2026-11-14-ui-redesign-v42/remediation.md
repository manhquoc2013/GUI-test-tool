# Remediation: s-review findings (layout logic)

**Status**: DONE  
**Parent**: SPEC.md / ADR 0004

## Must
1. [x] Remove continuous force-collapse of Explorer when dock open and W&lt;1440; open-dock policy only on open APIs; allow ☰ reopen in laptop band; mutual exclusion only when W&lt;1240.
2. [x] Regression test: `test_hamburger_reopens_explorer_with_dock_in_laptop_band`.

## Should
3. [x] `sync_drawer_geometry` = sizes restore only (no close_drawer / flag flips).
4. [x] Hidden panes: `setMinimumWidth(0)` / `setMaximumWidth(0)`; restore mins on show.
5. [x] Theme bind: `bind_widget_theme` disconnects on `destroyed` without capturing live widget methods.

## Nice
6. [x] Gitignore `data/copilot/`; explorer density labels Cases / EL / Reports.
