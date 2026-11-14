# Plan — Review Remediation

## Capability order

```text
cancel-foundation → heal-bind → orchestrator-copilot → ui-shell
  → secrets-keychain → nice-polish → mainwindow-split → docs-verify
```

## Streams

| Stream | Tasks | Parallel? |
|---|---|---|
| A Agent runtime | T1 cancel, T2 healer, T3 binder, T8 dual-loop verify | A then sequential |
| B Copilot | T4 clarify×stop, T5 critic merge, T6 history, T7 Gemini/MCP/heuristics | After T1 constants; parallel with A heal |
| C UI shell | T9 modals, T10 closeEvent, T11 Esc+busy, T12 push layout | After cancel API stable |
| D Secrets | T14 keyring | Independent early |
| E Nice | T15 stream chunks, T16 client reuse, T17 crew-memory rotate | After B |
| F Split | T18 Layout/Run/Copilot/Export controllers | After C+B stable |
| G Ship | T19 docs, T20 gates+review+learnings | Last |

## Risks

- Push layout must not break `test_airy_shell` overlay assertions — update tests for ≥960 push.
- Keyring in CI: use in-memory backend / monkeypatch.
- MainWindow split: keep public widget attrs used by tests (`right_drawer`, `copilot`, `h_splitter`, …).
