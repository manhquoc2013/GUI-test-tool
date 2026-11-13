# Plan: Zero-Friction UI Polish (Phase 4C)

Binding: `SPEC.md`, blueprint §6, UX review 7 blockers + A–E upgrades.

## Dependency graph

```text
T1 theme+qtawesome+constants
 ├── T2 action bar (main_window chrome)
 ├── T3 explorer split
 ├── T4 spec form (step_builder)
 ├── T5 browser+inspect+agent feed
 └── T6 drawer+datagrid+status pills
      └── T7 tests + gates + .ai-context
```

## Vertical slices

1. **Foundation** — tokens, qtawesome, QSS 8px grid.
2. **Action bar** — URL field, Sync badge, skill combo, Run/Stop once, Export menu, New Case / Wizard; remove duplicate right-side buttons.
3. **Explorer** — min 260px / ~22%, search, vertical splitter Suites vs Shared Elements, status dots.
4. **Spec** — form fields + amber UNBOUND banner; persist on capture/save.
5. **Browser & Agent** — Launch empty-state CTA, Inspect mode, pin element+bind step, step feed cards (no API-integration fluff).
6. **Drawer & status** — collapsible 32px header, DataGrid formatting, status pills (MCP / Skills / Token).
7. **Verify** — tests, ruff, mypy, pytest; refresh `.ai-context.md`.

## Risks

- macOS accessibilityLabel: always `deleteLater()` when swapping dock/body widgets; setAccessibleName on custom chrome.
- pytest-qt smoke must not rely on removed `_run_logs` QPlainTextEdit API — expose feed text accessor.
- Inspector without browser started: show VN guidance, do not crash.
