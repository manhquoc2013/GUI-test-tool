# SPEC — AI Wizard multi-document inputs

## Objective

AI Wizard Step 1 accepts **multiple** documents (SRS + test artefacts). Each file is **auto-classified**, **imported** into UNBOUND scenarios/cases when applicable, and **appended as LLM context** when generating the outline.

## Commands

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

## Requirements

1. **Formats**: Anything MarkItDown can convert; first-class structured parse for `.md`, `.xlsx`, `.csv`.
2. **Kinds** (auto, no manual picker): `srs` | `test_scenario` | `test_case`.
3. **Import**: Scenario/case docs become `TestScenario`/`TestCase` with `binding_status=UNBOUND` and appear in Step 2 checklist.
4. **LLM context**: Freeform markdown + all ingested markdown (tagged by kind) feed `SpecGenerator` outline.
5. **Merge**: Imported scenarios first; generated outline appended (skip duplicate `code`).
6. **UI**: Multi-file list with detected kind label; add/remove; keep URL/Figma/markdown fields.
7. **Validation**: Continue if markdown **or** ≥1 attached doc succeeds.

## Out of scope

- Manual kind override UI
- Round-trip fidelity for every custom Excel template
- Changing Copilot chat attach pipeline

## Testing

- Unit: classify heuristics, xlsx/csv/md import, context assembly, merge dedupe.
- UI: wizard still completes 3 steps; attaching a CSV/XLSX surfaces imported scenarios.
