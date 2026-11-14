# Learnings — Coverage completeness

- Completeness keys `(doc_id, chunk_index)`; never merge sections across docs.
- When `n_docs > max_sections`, keep ≥1 section/doc (may exceed budget) — never drop a doc.
- One `load_chunks` for gate+windows; status uses non-empty chunk count.
- Stop flush: orchestrator returns only on `ev.kind == "stopped"`.
- Ingest: missing scenario `id` → UUID; do not silent-drop.
- Removed unused `should_run_coverage` / `sum_document_chunks` (gate lives in `plan_coverage_windows`).
