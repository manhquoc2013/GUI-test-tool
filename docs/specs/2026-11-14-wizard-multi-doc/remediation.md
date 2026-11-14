# Remediation — s-review (wizard multi-doc)

Tracked from five-axis review + [Bugbot](174e9d06-043c-41dd-b4ad-7a74d2e434c8) + [Security Review](c425bb2c-360b-48e8-87d3-e49febf0e9e2).

## Must fix

1. [x] Prefer **structural** classify (headers/sheets) over filename tokens
2. [x] Deduplicate scenario rows on import
3. [x] Stop advertising `.xls`
4. [x] No phantom outline when imports exist

## Should fix

5. [x] Reset clears sample markdown (and docs)
6. [x] Do not overwrite imported `goto` `input_data` when wizard URL is set
7. [x] Normalize headers more aggressively (`TestCaseID` → `test case id`)
8. [x] Cap file bytes / CSV-XLSX rows
9. [x] Single XLSX parse path (openpyxl table markdown only)

## Nice to have

10. [x] MD with both scenario+case headings imports both
11. [x] Hint when re-attaching duplicate path
12. [x] Narrow file filter (no `All (*)`, no `.xls`; add `.tsv`)
13. [ ] Wire Figma into context (deferred — display-only pre-existing)

## Added (user)

- [x] Any tabular shape accepted; non-standard → loose heuristic + optional LLM parse; always SRS+docs LLM context

## Explicit non-issues vs SPEC

- Project-root sandbox like `qa-ingest` would **block** intentional off-project file picks — not a Must for Wizard; optional warn on symlink is enough.
