# Plan — Copilot History + Parallel Runs

1. **T1** `chat-run-registry`: RED→GREEN unit tests + `ChatRunRegistry` (start/cancel/finish/unread/live).
2. **T2** `chat-history-rail`: collapsible rail in Copilot; list/select/delete/new; restore last session on startup; empty prune.
3. **T3** `parallel-session-switch`: `on_prompt` keyed by session id; switch without hijack; route events to live session; per-view busy/stop + badges.
4. **T4** Full gates + `tasks/learnings.md` + mark todo complete.
