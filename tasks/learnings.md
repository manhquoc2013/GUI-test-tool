# Learnings — Phase 4C Zero-Friction UI

- Spec form signals recurse if `case_changed` handlers call `current_case()` during `setText`/`setCurrentText`; use `_loading` + `peek_case()`.
- VN-first labels + EN technical tokens (`BOUND`/`UNBOUND`) match blueprint §6 without i18n framework.
- Inspector pin-and-bind belongs in UI session layer (`pin_shared_element`) not dual-loop agent.
- `qtawesome` + QSS object names (`#primary`, `#bannerWarn`, `#statusPill`) give scannability without custom painted widgets.
- Never show `QMessageBox` from `@asyncSlot` while Playwright is running — nested loop vs `PipeTransport` → `RuntimeError: Cannot enter into task`. Use Fluent `InfoBar`.
- Unresolvable Target URL (`ERR_NAME_NOT_RESOLVED`) must not tear down the session: fallback `about:blank` and warn.
- macOS `Called accessibilityLabel on invalid object` is Cocoa querying destroyed Qt AX elements (Fluent animations + `TreeWidget.clear`). Filter stderr/qWarning; prefer hide-filter over rebuild-on-keystroke.
- Keep developer phrases (`agent API / integration tests`) out of AgentFeed filters.
