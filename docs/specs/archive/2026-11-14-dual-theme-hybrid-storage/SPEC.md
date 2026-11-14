# Spec: Dual-Theme Light Engine + Hybrid Local-First Storage

**Status**: ACTIVE  
**Product**: AI QA Test Studio  
**Date**: 2026-11-14  
**Design tokens**: `docs/Tài liệu Đặc tả Thiết kế Lại Giao diện & Kiến trúc Dual-Theme (v4.2).md` §2  
**Capability map**: `CAPABILITY_MAP.md`

## Objective

Ship zero-glitch Dark↔Light dual-theme (tokens §2.2, live `themeChanged`, TopBar toggle, Settings `auto|dark|light`) and replace JSON-only persistence with **Hybrid Local-First**: YAML/JSON scenarios (Git) + SQLite WAL/FTS5 (index + runtime) + filesystem artifacts — without losing `git diff` or forcing an external DB server.

**Success**: `uv run ruff check src tests` · `uv run mypy src` · `uv run pytest` green; theme persists via SQLite; open migrated project indexes in SQLite; Explorer search uses FTS5; CI headless remains green.

## Assumptions (locked in interview)

1. Scope = **B**: Dual-Theme + **full** Hybrid in one initiative.
2. Theme launch = **OS `Theme.AUTO`**; TopBar toggle persists override `dark|light`; Settings can reset to `auto`.
3. LIGHT tokens + toggle + **migrate all hardcoded dark QSS** now.
4. Layout:
   - `<project>/{suites,elements,data}/**/*.yaml` + `project.yaml` (Git)
   - `<project>/.studio/runtime.db` + `.studio/artifacts/` (gitignore)
   - App settings DB in OS app-data dir (theme/AI/browser — machine-local, not Git)
5. Auto-migrate `project.v4.json` → YAML tree + backup `.bak`.
6. Stack: `aiosqlite`, PRAGMA WAL/NORMAL/cache/mmap/FK as in user hybrid brief; `pyyaml` already present.
7. UI/Agent talk only to `StorageRepository`; `SqliteRepository` for local/CI.
8. Keep `QMainWindow` shell; no FluentWindow; keyring still holds API secrets (DB stores refs only).

## Commands

```
uv sync
uv add aiosqlite
uv run ruff check src tests
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
```

## Project structure (target)

```text
src/ai_qa_test_studio/
  constants.py                 # theme token keys, paths, .studio names
  core/
    storage.py                 # thin façade or deprecate → repositories
    storage_repository.py      # Protocol / ABC async
    sqlite_repository.py       # aiosqlite impl + PRAGMA + schema
    project_yaml.py            # YAML tree read/write + migrate v4 json
    index_sync.py              # incremental hash sync + FTS5
    env_manager.py             # settings via repository (not settings.json primary)
  ui/
    theme.py                   # ThemeTokens DARK/LIGHT, get_theme_tokens, apply_*
    theme_bind.py              # keep WeakMethod-safe bind (EX2)
    widgets/                   # ThemeStyleMixin or bind_widget_theme on all custom QSS
```

## On-disk contracts

| Data | Store | Git |
|---|---|---|
| Suites / cases / steps / EL / test data | YAML under project root | Yes |
| Search index, unbound counts, file hashes | `.studio/runtime.db` | No |
| Runs, step logs, heal audit, token telemetry | `.studio/runtime.db` | No |
| Screenshots, traces, HTML/Excel reports | `.studio/artifacts/` + path in DB | No |
| App settings (`theme`, browser, AI refs…) | App-data SQLite | No |

## Dual-theme rules (binding)

- Tokens match design doc §2.2 (`bg_canvas` / `bg_dock` / `bg_card` Light triad; action badge 700 contrast).
- No static `setStyleSheet` color in `__init__` without `_apply_theme_style` + `bind_widget_theme`.
- Zero-override on stock Fluent `TableWidget`/`LineEdit`/… backgrounds.
- Explicit `color:` on custom `QLabel` QSS; splitter handle uses `border_hairline`.
- Transparent `SmoothScrollArea` viewport rule.

## Testing strategy

- Unit: SQLite init/PRAGMA; settings migrate JSON→DB; theme resolve `auto|dark|light`; YAML round-trip + v4 migrate; incremental sync only re-parses changed hashes; FTS5 accent-insensitive hit.
- UI (pytest-qt): TopBar toggle flips `isDarkTheme`; badges restyle on `setTheme`; Settings combo includes `auto`.
- Regression: existing settings/keyring tests updated for DB backend; shell v4.2 tests still pass.

## Boundaries

- **In**: Hybrid storage foundation + Dual-Theme cutover + Explorer FTS wiring + run telemetry tables.
- **Out**: PostgreSQL team dashboard adapter (interface only); FluentWindow; changing agent/MCP semantics; shipping binary preview scripts.
- **Git**: One atomic commit per completed todo slice (s-agent). Do not commit secrets.

## Verification checklist

- [ ] Live Dark↔Light with Explorer/Steps/Drawer/Wizard/Settings open — no stale dark patches
- [ ] Light badges/banner WCAG-readable on `#F8FAFC`/`#FFFFFF`
- [ ] pytest create/destroy windows + `setTheme` — no `Internal C++ object already deleted`
- [ ] Open legacy `project.v4.json` migrates; YAML git-diffable
- [ ] FTS search Vietnamese không dấu
- [ ] Gates: ruff / mypy / pytest — 0 errors
