# Learnings — Copilot History Overlay + Remediation

- Narrow dock cannot host a side-by-side history rail; overlay drawer keeps chat full-width.
- Cancel must live only in `ChatRunRegistry` — a window-level `_copilot_cancel` pointer races with session switches.
- After `load_session`, `_streaming_row` is None; `end_role` must repaint by `message_id`, not only the streaming handle.
- Clarify/ingest must be turn-scoped (`session_id` / standalone ingest fallback) because the pane widget is shared across sessions.
