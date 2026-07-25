# Design: Case form — Lưu và ghi

**Date:** 2026-07-25  
**Status:** Approved in chat  
**App:** Playwright Automation Studio

## Goal

On **create/edit case** dialogs, add **🔴 Lưu và ghi**: save the case, open its suite workspace with that case selected, and start recording immediately.

## Decisions

| Topic | Choice |
|--------|--------|
| Empty URL | **Block** on dialog — cannot press Lưu và ghi without URL |
| Tạo / Lưu | Unchanged — URL still optional |
| Scope | Case forms only (Explorer + Workspace) |
| Approach | Extra `ButtonType` + `Result.startRecord` flag |

## UX

Button bar (case only): **❌ Hủy** · **✅ Tạo** / **💾 Lưu** · **🔴 Lưu và ghi**

- Lưu và ghi validates name (required) + URL (required, non-blank).  
- On success: persist → navigate to workspace → select case → `startRecord()`.

## Implementation notes

- Extend `EntityFormDialog.Result` with `boolean startRecord` (default `false` for other kinds / Tạo-Lưu).  
- Call sites that create/edit case check `form.startRecord()` after save.  
- Explorer: after create/edit, `openWorkspace` path equivalent + ensure workspace selects case and records.  
- Workspace: after create, select new case and `startRecord()`; after edit from workspace (if any), same.  
- Reuse existing URL guard inside `startRecord()` as safety net.
