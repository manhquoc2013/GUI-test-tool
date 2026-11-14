# SPEC — s-review remediation (Copilot / motion / ingest)

## Objective
Close every Must/Should finding from the 2026-11-14 deep s-review before merge: dock animation race, path-aligned ingest `doc_id`s, ingest cache lifecycle + tests, shared result-line helper, remaining CTX markers. Never stage `data/copilot/sessions.json`.

## Success
- Sidebar snap-close on narrow width **stops** dock width animation so open `_on_done` cannot re-check Copilot / unlock dock.
- `await_attachment_ingests` returns path→`doc_id` (skips failures without shifting peers); controller assigns by path.
- After chip clear, cached ingest still resolves `doc_ids`; `new_session` / `clear_messages` cancel tasks and clear cache.
- One shared result-line helper feeds `build_final_report` and bubble summary (no duplicated Coverage/VERDICT regexes).
- Context stops use `CTX_MARKER_DOM` / `CASE` / `LAST_ERROR` constants.
- Gates: `uv run ruff check src tests`, `uv run mypy src`, `uv run pytest -q` — 0 errors.

## Out of scope
- Splitting into multiple PRs (atomic commits suffice).
- Large orchestrator file split beyond marker/constants hygiene.
- Pushing to remote.

## Boundaries
- Theme via ThemeTokens + `bind_widget_theme` only.
- No secrets; restore sessions.json if dirty; do not commit it.
