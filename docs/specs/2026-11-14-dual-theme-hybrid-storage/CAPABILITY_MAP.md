# Capability Map: Dual-Theme + Hybrid Local-First Storage

**Status**: ACTIVE  
**Date**: 2026-11-14  
**Supersedes**: archived UI Redesign v4.2 (shell complete; theme still dark-hardcoded)

| Module id | Responsibility | Depends on |
|---|---|---|
| `storage-foundation` | `aiosqlite` + WAL PRAGMA; app DB + per-project `.studio/runtime.db`; `app_settings`; migrate `settings.json` | — |
| `storage-repository` | Async `StorageRepository` interface; `SqliteRepository` impl; UI/Agent call only via interface | `storage-foundation` |
| `project-yaml` | Split `project.v4.json` → Git-friendly YAML tree; auto-migrate + `.bak`; keep Pydantic models | `storage-repository` |
| `sqlite-index` | `test_cases_index` + FTS5; incremental sync by file hash/mtime | `project-yaml` |
| `runtime-telemetry` | `execution_runs`, healing audit, artifact path refs under `.studio/artifacts/` | `storage-foundation` |
| `dual-theme` | `ThemeTokens` Dark/Light; live refresh; TopBar toggle; `auto\|dark\|light`; migrate hardcoded QSS | `storage-foundation` |
| `explorer-fts` | Explorer `SearchLineEdit` → FTS5 (`unicode61 remove_diacritics 2`) | `sqlite-index` |

**Build order:**  
`storage-foundation` → `storage-repository` → (`project-yaml` ‖ `dual-theme` ‖ `runtime-telemetry`) → `sqlite-index` → `explorer-fts`

**Parallel after foundation:** `dual-theme` and `project-yaml` are independent once settings persist via repository.
