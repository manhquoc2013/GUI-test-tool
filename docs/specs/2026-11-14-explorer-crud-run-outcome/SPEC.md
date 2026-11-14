# Spec: Explorer CRUD + Batch Run + Expected Outcome Enum

**Status**: ACTIVE  
**Product**: AI QA Test Studio  
**Date**: 2026-11-14

## Objective

Restore Explorer rename/delete/run for case·scenario·suite·group (context menu + toolbar), sequential batch run with safe Stop and one aggregate HTML/Excel report, and Expected Outcome as a system `StrEnum` ComboBox without silently overwriting legacy YAML.

**Success**: `uv run ruff check src tests` · `uv run mypy src` · `uv run pytest` green; tree mutations via pure `project_tree_ops`; batch cancel sticky; legacy ExpectedOutcome preserved until explicit pick.

## Locked decisions

See plan invariants: re-entrancy busy guard, `reset_cancel`/`emit_report`, Scenario parity, `protect_root`, CANCELLED stubs, `extend(sub_report.cases)`, `_outcome_touched`, index sync after rename/delete.

## Commands

```
uv run ruff check src tests
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
```

## Boundaries

- No runtime assert ExpectedOutcome ↔ PASS/FAIL.
- No parallel run / stop-on-first-fail.
- No new report formats (PDF/Allure/JUnit).
- No drag-drop move.
