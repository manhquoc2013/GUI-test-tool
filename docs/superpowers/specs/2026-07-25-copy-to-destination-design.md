# Design: Copy suite/case to another destination

**Date:** 2026-07-25  
**Status:** Approved (conversation) — awaiting user review of this spec  
**App:** Playwright Automation Studio (JavaFX)

## Goal

Let users copy a **suite into another group** and a **case into another suite**, with a searchable destination dropdown and **hard block** on duplicate names in the target scope.

## Decisions (locked)

| Topic | Choice |
|--------|--------|
| Duplicate name | **Block** — show error, disable OK until name is unique in target |
| Dialog shape | **One dialog** — new name + destination; default = current location |
| Destination label | **Full path** — e.g. `Dự án / Nhóm A / Nhóm con` or `Nhóm A / Smoke` |
| Implementation | No new UI library — editable `ComboBox` + `FilteredList` |

## Out of scope

- Copying groups
- Moving (cut) instead of copy
- Auto-suffix `(copy 2)` on conflict
- Separate “copy in place” vs “copy to…” menu items

## UX

### Entry points

- Explorer context menu **📄 Sao chép** (suite / case) — same as today  
- Toolbar **Sao chép** when suite/case selected — same as today  

### Dialog fields

1. **Tên mới** — `TextField`, default `"{name} (copy)"`  
2. **Đích** — searchable combo  
   - Suite copy → list of **groups** (including project root as `Dự án`)  
   - Case copy → list of **suites** (every suite under the project tree)  
3. **Error line** — red helper under fields when invalid  

### Searchable combo behavior

- User can type to filter by path (case-insensitive contains)  
- Selecting an item commits that destination  
- Typing a string that matches no item → invalid (OK disabled)  
- Default selection = current parent group (suite) or current suite (case)

### Validation (live + on OK)

| Rule | Message (VI) |
|------|----------------|
| Name blank | `Tên không được để trống` |
| Destination missing / not in list | `Chọn đích hợp lệ` |
| Suite name exists as folder under target group | `Suite "{name}" đã tồn tại trong nhóm đích` |
| Case name exists in target suite (by name) | `Case "{name}" đã tồn tại trong suite đích` |

OK enabled only when all rules pass.

### Success

- Persist copy via `ProjectStore`  
- Refresh Explorer tree  
- Toast success with new name  
- Prefer re-selecting the copied node when practical  

## Data / store

### Existing

- `copySuite(Path fromDir, Path toGroupDir, String newName)` — already refuses if `toGroupDir/newName` exists  
- `copyCase(Path suiteDir, String caseId, String newName)` — copies **within the same suite** only  

### Changes

1. **`copyCase(Path fromSuiteDir, String caseId, Path toSuiteDir, String newName)`**  
   - Resolve source case in `fromSuiteDir`  
   - Ensure name unique among cases in `toSuiteDir` (case-insensitive recommended for UX consistency with folder names on disk for suites)  
   - New id, clone assertions/meta, copy `.java` script into **target** suite dir  
   - Append to target `suite.json`  
   - Keep old same-suite overload as thin wrapper calling the new method with `toSuiteDir == fromSuiteDir`, or replace call sites  

2. **Listing helpers** (on `ProjectStore` or small helper used by dialog):  
   - `listGroupsForCopy()` → `{ pathLabel, groupDir }` including project root  
   - `listSuitesForCopy()` → `{ pathLabel, suiteDir }`  
   - Path labels built while walking the on-disk tree (`group.json` / `suite.json`), joined with ` / `  

3. **Existence checks** (explicit, for dialog validation before IO):  
   - `suiteNameExists(Path groupDir, String name)` → `Files.exists(groupDir.resolve(name))` (directory that is a suite)  
   - `caseNameExists(Path suiteDir, String name)` → any case in `suite.json` whose name equals `name` after trim, **case-insensitive**

Suite uniqueness stays **folder-name** based (same as today’s `copySuite`). Dialog and store must use the same rules.

## UI implementation

- New dialog helper alongside `EntityFormDialog`, e.g. `CopyEntityDialog`  
  - `showCopySuite(String defaultName, Path currentGroupDir, List<GroupOption> groups)`  
  - `showCopyCase(String defaultName, Path currentSuiteDir, List<SuiteOption> suites)`  
  - Returns `Optional<CopyResult(name, targetDir)>`  
- Wire searchable combo: `ComboBox<Option>` + `FilteredList` on editor text; converter shows `pathLabel`  
- Apply `DialogTheme.apply` like other dialogs  
- Replace `TextInputDialog` body in `ExplorerPageController.copyItem()` with this dialog  

## Error handling

- Store throws `IOException` / `IllegalArgumentException` with Vietnamese messages → toast error  
- Dialog should prevent most duplicates before OK; store remains the last line of defense (race / stale list)

## Testing

- Copy suite to same group with unique name → OK  
- Copy suite to same group with existing name → blocked in dialog  
- Copy suite to another group → appears under that group with scripts/asserts intact  
- Copy case within suite / across suites → unique vs duplicate name  
- Filter combo by substring of path  

## Architecture sketch

```
ExplorerPageController.copyItem()
  → build group/suite option lists (ProjectStore walk)
  → CopyEntityDialog.showCopySuite|Case(...)
  → ProjectStore.copySuite | copyCase(from, to, name)
  → refreshTree + toast
```
