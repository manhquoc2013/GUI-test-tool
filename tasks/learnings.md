# Learnings — Phase 4C Zero-Friction UI

- Spec form signals recurse if `case_changed` handlers call `current_case()` during `setText`/`setCurrentText`; use `_loading` + `peek_case()`.
- VN-first labels + EN technical tokens (`BOUND`/`UNBOUND`) match blueprint §6 without i18n framework.
- Inspector pin-and-bind belongs in UI session layer (`pin_shared_element`) not dual-loop agent.
- `qtawesome` + QSS object names (`#primary`, `#bannerWarn`, `#statusPill`) give scannability without custom painted widgets.
- Never show `QMessageBox` from `@asyncSlot` while Playwright is running — nested loop vs `PipeTransport` → `RuntimeError: Cannot enter into task`. Use Fluent `InfoBar`.
- Unresolvable Target URL (`ERR_NAME_NOT_RESOLVED`) must not tear down the session: fallback `about:blank` and warn.
- macOS `Called accessibilityLabel on invalid object` is Cocoa querying destroyed Qt AX elements (Fluent animations + `TreeWidget.clear`). Filter stderr/qWarning; prefer hide-filter over rebuild-on-keystroke.
- Keep developer phrases (`agent API / integration tests`) out of AgentFeed filters.
- Fluent layout: Expanding URL + no btn `minimumWidth` clips `Run`; right pane 340px clips long Inspect/Launch labels — shorten labels + raise pane min width. `ScreencastViewport.set_placeholder` must show status on the canvas stack (not always empty CTA) or nav-fail looks like a black void. Legacy Java rows use `expectedOutcome` camelCase.
- Copilot: default orchestrator is Planner→Composer→Critic (one Critic pass) — not free-form multi-agent chat. Undo user turn must drop all following role artifacts and refill input.
- No API key → `LLM_PROVIDER_OFFLINE` keeps UI/tests deterministic; live OpenAI-compat/Gemini only when `api_key_ref` set in EnvManager.
- Wire Copilot with `@asyncSlot` + `asyncio.Event` cancel — never nested QMessageBox during stream.
