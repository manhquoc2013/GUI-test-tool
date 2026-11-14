# SPEC — Coverage Pass completeness

## Objective
Mỗi chunk (kể cả multi-doc attach) vào ≥1 Composer window; không truncate im lặng; status trung thực; không fallback compose Top-K sau khi đã chạy Coverage.

## Success
- Completeness key `(doc_id, chunk_index)` — test multi-doc fail nếu drop một doc.
- Coverage path: **một** `load_chunks` (gate + windows); status dùng `plan.total_chunks`.
- Empty scenarios → `mark_compose` + status lỗi; không để board trống.
- Stop giữa pass → flush partial `apply_ready` nếu đã có scenario.
- Coverage orchestration: `agent/coverage_pass.py`.
- `max_sections` luôn ≥ 1; notes chỉ “ok” khi window có scenario.
