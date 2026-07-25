# Design: Global stop test run

**Date:** 2026-07-25  
**Status:** Approved in chat  
**App:** Playwright Automation Studio

## Goal

One global **⏹ Dừng** control that hard-aborts the current test run immediately (including the case in progress).

## Decisions

| Topic | Choice |
|--------|--------|
| Placement | Topbar only; visible while a run is active |
| Abort | Hard — close Playwright session now; do not wait for current case |
| Scope | Test runs only (`TestRunnerService`), not recording |
| Remaining cases | `SKIPPED` + reason “Đã dừng bởi người dùng” |
| Interrupted case | `ERROR` + failureReason “Đã dừng bởi người dùng” |
| Report | Still saved if `total > 0` |

## Approach

`RunCancelService`: `beginRun` / `endRun`, `requestCancel`, `isCancelRequested`, attach active `SharedBrowserSession` + worker thread. Cancel sets flag, closes session (unblocks Playwright), interrupts worker. `TestRunnerService` checks flag each case and after harness errors. Main shell enables stop only while a run is active.

## Out of scope

- Soft “finish current case then stop”
- Stop recording via this button
- Live progress UI (separate plan)
