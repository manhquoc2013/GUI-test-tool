# Remediation — s-review (wizard multi-doc)

Tracked from five-axis review + [Bugbot](174e9d06-043c-41dd-b4ad-7a74d2e434c8) + [Security Review](c425bb2c-360b-48e8-87d3-e49febf0e9e2).

## Must fix

1. Prefer **structural** classify (headers/sheets) over filename tokens — filename `*scenario*` / `*spec*` must not drop case rows
2. Deduplicate scenario rows on import; never emit empty duplicate `TS_*` codes
3. Stop advertising `.xls` (openpyxl cannot load legacy BIFF) — drop from filter/suffixes or convert explicitly
4. When imports already produced scenarios, do **not** always append offline `TS_HAPPY_01` / `TS_NEG_01` phantoms (generate only if no import, or mark generated distinctly / skip offline stubs)

## Should fix

5. Reset clears sample markdown (and docs) so reopen does not keep seed SRS
6. Do not overwrite imported `goto` `input_data` when wizard URL is set (set `start_url` only, or only fill empty goto)
7. Normalize headers more aggressively (`TestCaseID` → match `test case id`)
8. Cap file bytes / CSV-XLSX rows before full parse (DoS / UI freeze)
9. Prefer single XLSX parse path (openpyxl **or** MarkItDown, not both)

## Nice to have

10. MD with both scenario+case headings imports both
11. Hint when re-attaching duplicate path
12. Narrow `All (*)` filter
13. Wire Figma into context only when product wants it (field currently display-only; pre-existing)

## Explicit non-issues vs SPEC

- Project-root sandbox like `qa-ingest` would **block** intentional off-project file picks — not a Must for Wizard; optional warn on symlink is enough.
