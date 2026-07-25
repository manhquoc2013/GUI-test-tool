# Entity metadata MVP — Design

**Date:** 2026-07-25  
**Status:** Approved in chat (option A / approach 1)  
**App:** Playwright Automation Studio

## Goal

When the user selects a group / suite / case, show real metadata. In suite workspace, allow editing peripheral info (description) and viewing created/updated timestamps plus a short automatic change history.

## Non-goals (this MVP)

- Manual history notes or history filters
- Diff of script contents in history
- Separate metadata popup / global sidebar
- Full audit / multi-user authorship

## Data model

Shared fields on **TestGroup**, **TestSuite**, **TestCase** (JSON via Jackson, `ignoreUnknown = true`):

| Field | Type | Notes |
|-------|------|--------|
| `description` | String | Already exists; editable |
| `createdAt` | String (ISO-8601) | Set once on create; backfill on first load if missing |
| `updatedAt` | String (ISO-8601) | Bumped on metadata/content changes that we track |
| `history` | List≤20 of `HistoryEntry` | Newest first |

### HistoryEntry

```text
at: ISO-8601
action: short code (CREATED, RENAMED, RECORDED, CODE_SAVED, STATUS_CHANGED, DESCRIPTION_UPDATED, …)
detail: human-readable Vietnamese one-liner
```

Helper: `EntityMeta.touch(entity, action, detail)` — sets `updatedAt`, prepends history, trims to 20.

Persist in existing files:

- Group → `group.json`
- Suite + cases → `suite.json`

## Explorer

Enhance right preview panel when a node is selected:

- Name, type, status (existing)
- **Real description** (from loaded JSON; empty → “Chưa có mô tả”)
- Created / updated timestamps (formatted local)
- Last ~5 history lines
- Counts (existing)

**Edit description on Explorer** for Group and Suite:

- `TextArea` + button **Lưu mô tả**
- Case: show description read-only in preview; editing primarily in Workspace Info tab (optional: same save on Explorer if cheap)

## Workspace

New tab **ℹ️ Thông tin** beside Code / Asserts:

1. **Suite block:** description (editable), created/updated, suite history list  
2. **Case block** (when a case is selected): description (editable), created/updated, case history; URL remains on toolbar (already per-case)

**Lưu thông tin** saves suite.json (suite + selected case fields).  
Dirty description → same leave-guard pattern as unsaved code when switching case/tab/back (extend or reuse dialog).

## Auto history events

| Event | Where |
|-------|--------|
| Create group/suite/case | ProjectStore create* |
| Rename | Explorer rename |
| Record / re-record | Workspace after codegen OK |
| Save code | Workspace saveCode |
| Status → Ready | After record / status update |
| Save description | Explorer or Workspace Info |

## Migration

On `loadAndMigrateSuite` / load group:

- If `createdAt` blank → set to now (or file mtime if easy; else now)
- If `updatedAt` blank → copy `createdAt`
- Ensure `history` non-null list

No separate migration file.

## UI / UX notes

- Dark-theme readable labels; keep Tokyo Night CSS
- Timestamps display: `yyyy-MM-dd HH:mm`
- History ListView or Labels, read-only
- Do not block record/run if description empty

## Verification

1. Create group → preview shows createdAt + history “Tạo nhóm”  
2. Edit suite description in Explorer → persists after refresh  
3. Open workspace → Info tab shows suite meta; select case → case meta  
4. Record / save code → history gains entries; updatedAt changes  
5. Old suite.json without new fields still loads

## Out of scope follow-ups

- Option B: manual history notes  
- Group workspace (groups stay Explorer-only)
