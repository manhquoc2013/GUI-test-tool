# Plan: Dual-Theme + Hybrid Storage

## Dependency graph

```
storage-foundation (app DB + project runtime.db + app_settings + migrate settings.json)
        │
        ├── storage-repository (Protocol + SqliteRepository)
        │         │
        │         ├── project-yaml (tree + migrate project.v4.json)
        │         │         │
        │         │         └── sqlite-index (hash sync + FTS5)
        │         │                   │
        │         │                   └── explorer-fts (SearchLineEdit)
        │         │
        │         └── runtime-telemetry (execution_runs + artifacts paths)
        │
        └── dual-theme (tokens + mixin bind + TopBar + Settings auto)
```

## Vertical slices

1. **App can open SQLite with WAL and round-trip a setting key** (foundation + tests).
2. **EnvManager loads/saves AppSettings via DB**; JSON file migrates once.
3. **ThemeTokens + apply_theme(auto|dark|light)**; hardcoded surfaces refresh; TopBar toggle persists.
4. **Project YAML tree save/load + migrate v4 JSON**.
5. **Index sync + FTS5**; Explorer search wired.
6. **execution_runs + artifact path API**; gitignore `.studio/`.
7. **Gates + .ai-context.md + learnings**.

## Risks

| Risk | Mitigation |
|---|---|
| Sync UI thread blocked by SQLite | `aiosqlite` + qasync; never sync I/O on GUI thread for index rebuild |
| settings.json tests break | Keep migrate path; update tests to assert DB primary |
| Theme AUTO vs qfluentwidgets | Resolve OS then `setTheme(DARK/LIGHT)`; store preference `auto` separately |
| Large migrate of monolithic JSON | Atomic write YAML tree; leave `.bak`; fail closed with log |
| Thrashing on theme QSS | Central tokens; ban new hardcoded colors in review |

## Commit policy

One commit per completed todo item after gates for that slice pass.
