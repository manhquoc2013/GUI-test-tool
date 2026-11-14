# Learnings — s-review remediation (2026-11-14)

- Dock open/close `on_done` must be cancelled via `stop_channel` before snap-close; flipping `_dock_animating` alone is not enough.
- Sparse `list[doc_id]` + `zip(attachments, …)` mis-labels peers; return `dict[path, doc_id]`.
- Chip clear must keep ingest cache for the in-flight send; `new_session` / `clear_messages` must reset it.
- Agent must not import UI: put shared bubble/crew synthesis in `agent/result_snippet.py`.
- Never stage `data/copilot/sessions.json`.
