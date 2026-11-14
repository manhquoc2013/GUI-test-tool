# Learnings — Coverage completeness

- Completeness keys `(doc_id, chunk_index)`; index-only false-greens multi-attach.
- After Coverage statuses, never leave `compose_text` empty — heuristic Top-K composes again.
- One corpus load for gate+windows (`plan_coverage_windows` + `min_chunks`).
- Stop flush must finish before orchestrator aborts: return only on `ev.kind == "stopped"`, not bare `stop.is_set()`.
- `TestScenario` requires `id`; silent validate skips drop scenarios without tests noticing.
