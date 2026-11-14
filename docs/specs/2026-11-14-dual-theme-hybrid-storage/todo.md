# Todo: Dual-Theme + Hybrid Storage

**Progress**: 4/12 tasks (33%)

- [x] S0: SPEC + CAPABILITY_MAP + plan (this folder); archive v4.2
- [x] S1: Add `aiosqlite`; constants for `.studio` / app DB paths; `init_high_speed_db` + schema (`app_settings`, stubs for index/runs)
- [x] S2: `StorageRepository` + `SqliteRepository`; wire `EnvManager` settings load/save through DB; migrate `settings.json`
- [x] S3: `ThemeTokens` DARK/LIGHT + `get_theme_tokens`; rewrite `theme.py` apply helpers; tests for token resolve
- [ ] S4: TopBar theme toggle + Settings combo `auto|dark|light`; persist via settings repo; launch respects AUTO
- [ ] S5: Migrate hardcoded dark QSS widgets (explorer, editor, topbar, chat, browser, settings hints, badges, drawer, status)
- [ ] S6: Project YAML tree writer/reader (`project.yaml`, suites/elements/data); replace single-file `ProjectStorage.save/load`
- [ ] S7: Auto-migrate `project.v4.json` → YAML + `.bak`; unit tests round-trip
- [ ] S8: Incremental index sync + FTS5 populate; unit tests hash skip + accent search
- [ ] S9: Wire Explorer search to FTS5; keep empty/error states
- [ ] S10: `execution_runs` + artifacts path helpers; `.gitignore` `.studio/`; smoke API tests
- [ ] S11: Full gates (ruff/mypy/pytest) + `.ai-context.md` + `learnings.md`
