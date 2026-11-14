# Learnings — Dual-Theme + Hybrid Storage

## Decisions
- Theme preference: `auto|dark|light` in AppSettings SQLite blob; TopBar toggle writes explicit dark/light override; Settings can reset to `auto`.
- Light surfaces use solid triad (`#F3F4F6` / `#F8FAFC` / `#FFFFFF`), not inverted black overlays.
- Hybrid split: YAML scenarios (Git) + `.studio/runtime.db` index/runs (no Git) + artifacts paths only.
- Sync UI ↔ aiosqlite via `run_sync` thread bridge to avoid nesting qasync loops.

## Patterns
- Custom QSS always through `_apply_theme_style` + `bind_widget_theme`.
- `ProjectStorage` is a façade over `ProjectYamlStorage`; legacy `project.v4.json` migrates once with `.bak`.
- Explorer search: local substring OR FTS5 hit ids (accent-insensitive).

## Gotchas
- Do not put signal connects inside `_apply_theme_style` (easy merge mistake).
- FTS5 element rows also need hash rows in `test_cases_index` or every sync recounts as updated.
- `uv.lock` is gitignored in this repo; `pyproject.toml` is the dependency source of truth.
