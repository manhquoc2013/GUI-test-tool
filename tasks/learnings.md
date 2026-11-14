# Learnings — Copilot History + Parallel Runs

- Prefer live `CopilotSession` from `ChatRunRegistry` over store snapshots when switching back to a running chat; the store lags until `finish_run`.
- UI mutations must be gated by “viewing this session id”; otherwise a background turn paints into the wrong transcript.
- Empty sessions clutter the rail — persist only `has_user_messages()`, and `prune_empties()` on save.
- Per-view Stop = `registry.request_cancel(viewed_id)`; do not share one global cancel Event across concurrent turns.
- `new_session` / history switch must clear pane busy even if another session is still generating.
