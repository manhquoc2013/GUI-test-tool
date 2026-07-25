# Design: Live run progress UI

**Date:** 2026-07-25  
**Status:** Approved (plan)  
**App:** Playwright Automation Studio

## Goal

Show live run progress on **Workspace case list** and **Explorer tree**: current case = Đang chạy; finished = Đạt / Thất bại / Bỏ qua / Lỗi. After run ends, keep overlays ~4s then restore lifecycle status.

## Decisions

| Topic | Choice |
|--------|--------|
| Surfaces | Workspace + Explorer |
| Events | Structured via `RunProgressService` (not log parsing) |
| Retention | ~4 seconds after `endRun`, then clear |
| Suite/group | Aggregate: RUNNING if any child running; else failed > error > passed > skipped |

## API

- `beginRun(scope, scopePath)`
- `caseStarted(CaseResult)` / `caseFinished(CaseResult)`
- `endRun(RunReport)` — schedule clear
- `clearNow()` — tests / immediate clear
- `statusForCaseId`, `overlayForSuiteDir`, `overlayForGroupDir`

## Out of scope

- Persist badges across restart  
- Change report format  
(Stop button is separate feature)
