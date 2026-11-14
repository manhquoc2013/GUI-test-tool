# Plan: Explorer CRUD + Batch Run + Expected Outcome

Mirror of implementation order (see Cursor plan `explorer_crud_run_outcome`):

1. Spec docs (this folder)
2. ExpectedOutcome StrEnum + TestDataRowDialog
3. `core/project_tree_ops.py` + unit tests
4. Explorer payloads, context menu, snapshot/restore, mutation gate
5. Session `reset_cancel`/`emit_report` + RunController `run_cases`
6. MainWindow + TopToolbar wire
7. Gates green
