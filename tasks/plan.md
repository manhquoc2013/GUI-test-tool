# Plan — s-review remediation

## Dependency order
1. **T1 Motion stop API + dock snap** — foundation for layout race
2. **T2 Path-aligned ingest return** — API change for controller
3. **T3 Ingest cache lifecycle + tests** — builds on T2
4. **T4 Shared result_line** — independent of T1–T3 but after green gates
5. **T5 CTX markers DOM/CASE/LAST_ERROR** — small constants + orchestrator
6. **T6 sessions restore + learnings + full gates**

## Parallelism
- T4 can run after T1–T3 land (or in parallel if no file overlap with bubble/crew).
- T5 independent of T2–T4 except orchestrator touch — sequence after T4 to avoid thrash.
