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

1. **Formats**: MarkItDown for docs; tabular test artefacts accept **any table shape** (`.csv`/`.tsv`/`.xlsx`/`.xlsm` + sheets). No legacy `.xls` in the picker.
2. **Kinds** (auto): `srs` | `test_scenario` | `test_case`. **Structural headers/sheets beat filename tokens.**
3. **Standard import**: Recognized IEEE/exporter-like columns → structured UNBOUND scenarios/cases.
4. **Non-standard tables**: Still classified as test artefacts; raw table stays in **LLM context with SRS**; parse via **LLM** when available (`async_bridge`), else loose heuristic column mapping — never silent drop.
5. **Merge**: Imported first; generate outline only when import list is empty (no phantom stubs alongside imports).
6. **UI**: Multi-file list + kind label; reset clears sample markdown; URL sets `start_url` without clobbering imported goto data; size/row caps on ingest.
7. **Validation**: Continue if markdown **or** ≥1 attached doc succeeds.

## Out of scope

- Manual kind override UI
- Round-trip fidelity for every custom Excel template
- Changing Copilot chat attach pipeline

## Testing

- Unit: classify heuristics, xlsx/csv/md import, context assembly, merge dedupe.
- UI: wizard still completes 3 steps; attaching a CSV/XLSX surfaces imported scenarios.
