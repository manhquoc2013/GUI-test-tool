# Explorer entity forms + run — Design

**Date:** 2026-07-25  
**Status:** Approved in chat (approach 1 + option B)  
**App:** Playwright Automation Studio

## Goal

- **Suite mới / Case mới** modals collect all basic fields needed to create and later run entities.
- Selecting a suite or case in Explorer shows those fields in the right **Xem trước** panel, with **Save**, **Run** (type-specific), and **Open Workspace**.
- Persist per-entity **browser** / **headless** with inheritance; show **createdAt** / **updatedAt** (read-only).
- Workspace: move **Case mới** beside the case-list header; replace “Chuột phải để thao tác” with a **?** tooltip; slim the toolbar (URL/browser/headless leave the global bars as specified below).

## Non-goals

- History / audit log (separate metadata MVP)
- Group create modal expansion beyond name (unchanged)
- Editing timestamps or manual history notes
- Portable packaging / JDK bundling

## Data model

Add to `TestSuite` and `TestCase` (Jackson `ignoreUnknown = true`):

| Field | Suite | Case | Notes |
|-------|-------|------|--------|
| `description` | ✓ | ✓ | Already exists |
| `status` | ✓ | ✓ | Already exists |
| `startUrl` | — | ✓ | Already exists |
| `browser` | ✓ | ✓ | Blank / null / «Mặc định» = không chọn → resolve default (see below) |
| `headless` | ✓ | ✓ | `Boolean` null = inherit default from suite then Settings |
| `createdAt` | ✓ | ✓ | ISO-8601 in JSON; set once on create; backfill on first load if missing |
| `updatedAt` | ✓ | ✓ | ISO-8601 in JSON; bump on save of metadata from preview / create |

### Timestamp display

- **Ghi** `createdAt` / `updatedAt`: lấy **thời điểm hiện tại theo local time** của máy (không dùng UTC tường minh trên UI).
- **Hiện** mọi chỗ UI **luôn** format local:

`HH:mm:ss dd/MM/yyyy`

Ví dụ: `14:05:09 25/07/2026`.  
JSON có thể lưu ISO-8601 có offset/zone local (hoặc instant rồi format lại local lúc đọc) — miễn hiển thị đúng giờ máy người dùng.

### Browser selection + default (run / record)

UI combo always includes first option: **«Mặc định»** (no specific browser selected).

**«Mặc định»** = **Playwright Chromium** (browser do Playwright quản lý / bundled), không phải Chrome/Edge hệ thống trừ khi user chọn rõ.

**Resolve browser when running / recording:**

1. Case has non-blank `browser` (not default) → use case  
2. Else suite has non-blank `browser` → use suite  
3. Else → **Playwright Chromium** (`CHROMIUM`) — đây là default app; `SettingsStore` nếu lệch thì vẫn coi default resolve là Chromium Playwright trừ khi Settings được đổi có chủ đích; **create/UI «Mặc định» luôn map → `CHROMIUM`**

Same chain for `headless`: case → suite → Settings (`headlessDefault`).

Persist: if user leaves «Mặc định», store `browser` as `""`; at resolve time `""` → `CHROMIUM` (Playwright). Create modals default the combo to «Mặc định».

## Create modals

Replace bare `TextInputDialog` for suite/case with a small custom dialog (FXML or programmatic `Dialog` + form), styled via `DialogTheme` (scrim / click-outside / Cancel).

### Suite mới

- Name (required)
- Description
- Status: Draft / Active / Inactive
- Browser: combo **«Mặc định (Playwright Chromium)»** + `BrowserDetector` list — leave default → run with Playwright Chromium
- Headless: **Ẩn trình duyệt**; null/unset follows suite then Settings (`headlessDefault`)

### Case mới (Explorer + Workspace)

- Name (required)
- Description
- Status: Draft / Ready / Inactive (align with existing case statuses)
- Start URL
- Browser + headless (same rule: **«Mặc định»** = Playwright Chromium, after suite inheritance if suite set a browser)

On success: create via `ProjectStore`, set `createdAt`/`updatedAt`, refresh tree / case list, select new node, refresh preview.

## Explorer preview (option A)

When selection changes:

| Node | Show | Actions |
|------|------|---------|
| Group / Project | Name, type, stats, description if any (read-only or light edit OK) | No entity Run |
| Suite | Editable: description, status, browser, headless; read-only: created/updated, case count | **Lưu**, **▶️ Chạy Suite** (only if Active), **Mở Workspace** |
| Case | Editable: description, status, startUrl, browser, headless; read-only: created/updated, parent suite | **Lưu**, **▶️ Chạy Case** (case Ready/Active **and** parent suite Active), **Mở Workspace** |

- **Lưu** writes `suite.json` (suite fields and/or the case within it), bumps `updatedAt`.
- Dirty form: optional warn on selection change (nice-to-have; if cheap, do it; else toast on discarded switch).
- Reuse existing `runNode` / `openWorkspace` wiring; labels switch Suite vs Case.
- Toolbar **Chạy mục chọn** remains.

## Workspace layout (same delivery)

- Remove global toolbar rows for URL + browser + headless (or leave URL only when a case is selected — **prefer:** URL editable in a compact case strip / keep per-case URL when case selected; browser/headless for record/run read from case→suite→settings, with optional compact controls later).
- **Minimum for this change:**
  - **Case mới** button on the same row as **Danh sách Case**
  - Replace “Chuột phải để thao tác” with a **?** control + tooltip text equivalent
  - Keep **Ghi hình lại**, **Chạy Suite**, stop-on-first-failure, share-context on the action toolbar
  - **Case mới** modal uses the full case form (not name-only)
- Record/run resolve browser/headless from selected case/suite as above; if toolbar combos removed, no longer read from removed controls.

## Persistence / store

- Extend `ProjectStore.createSuite` / `createCase` (and any update helpers) to accept/set new fields + timestamps.
- Backfill `createdAt`/`updatedAt` when loading suite.json if absent (set both to now once, or file mtime if easy — **prefer now** for simplicity).
- No migration file rewrite required beyond save-on-touch.

## Out of scope reminders

- History entries / Info tab from `2026-07-25-entity-metadata-design.md` (can merge later)
- Changing Group modal

## Success criteria

1. Suite/Case create modals expose name, description, status, browser, headless; case also URL.  
2. Click suite/case in Explorer shows those fields + timestamps; Save persists; Run uses resolved browser/headless and existing status gates.  
3. Workspace Case mới sits beside case list header; **?** tooltip replaces right-click hint text.  
4. Existing suites/cases without new keys still load and run via Playwright Chromium fallback.  
5. Browser «Mặc định» / empty → resolve **Playwright Chromium** (`CHROMIUM`), after case→suite inheritance.  
6. Timestamps: ghi bằng giờ local hiện tại; UI luôn `HH:mm:ss dd/MM/yyyy` (local).
