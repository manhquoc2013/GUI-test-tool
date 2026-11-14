# SPEC — Coverage Pass completeness

## Objective
Mỗi chunk (kể cả multi-doc attach) vào ≥1 Composer window; không truncate im lặng; status trung thực; không fallback compose Top-K sau khi đã chạy Coverage.

## Success
- Completeness key `(doc_id, chunk_index)` — test multi-doc fail nếu drop một doc.
- Merge section **không** gộp cross-`doc_id` (kể cả khi `max_sections` thấp).
- Coverage path: một `load_chunks`; status dùng số chunk non-empty.
- Empty scenarios → `mark_compose`; Stop → partial `apply_ready` rồi `stopped`.
- Ingest: thiếu `id` → tự gán UUID (không drop im lặng).
