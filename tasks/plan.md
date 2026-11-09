# Implementation Plan: System-Wide Review Round 2 Fixes & Optimizations

## Slice 1: H2 Index Consistency in `ProjectStore` & `LocalDataIndexService` (Findings #1, #2, #4)
- **Files**:
  - `src/main/java/com/studio/db/LocalDataIndexService.java`
  - `src/main/java/com/studio/store/ProjectStore.java`
- **Changes**:
  - Update `LocalDataIndexService.deleteGroup(String groupName)` to cascade-delete `test_assertions`, `test_cases`, and `test_suites` belonging to `groupName` before deleting from `test_groups`.
  - Add `private void syncSuiteIndex(Path suiteDir, TestSuite suite)` in `ProjectStore.java` and invoke it after `writeJsonAtomic` in all suite/case/assertion mutation methods (`updateSuiteMeta`, `updateCaseMeta`, `reorderCases`, `reorderAssertions`, `updateSuiteStatus`, `updateCaseStatus`, `updateCaseStartUrl`, `addAssertion`, `updateAssertion`, `deleteAssertion`, `replaceAssertions`, `updateCaseStepsAndData`, `updateCasePriority`, `renameCase`, `copyCase`).
  - Call `indexService.indexGroup(group, groupDir)` in `updateGroupMeta`.
  - Update `ProjectStore.renameGroup` to re-index all child suites under `newDir` with the new group name and directory path.
  - Enhance `ProjectStore.collectSingleCase(Path scopePath)` to match `c.getScriptFile()`, `c.getName()`, and `StringEscapeUtil.safeFileName(c.getName(), "case")` case-insensitively.

## Slice 2: Report Export Archival & Default Export Directory (Finding #3)
- **Files**:
  - `src/main/java/com/studio/service/ExcelReportService.java`
  - `src/main/java/com/studio/service/HtmlReportService.java`
  - `src/main/java/com/studio/controller/ReportDetailPageController.java`
  - `src/main/java/com/studio/controller/ExplorerPageController.java`
  - `src/main/java/com/studio/controller/SuiteWorkspacePageController.java`
- **Changes**:
  - Call `new AiAgentIndexService().archiveAndIndexExport(...)` at the end of `ExcelReportService.export` (`EXCEL_REPORT`) and `HtmlReportService.export` (`HTML_REPORT`).
  - Configure `FileChooser` initial directory to `StudioPaths.getExportsDir().toFile()` in `ReportDetailPageController`, `ExplorerPageController`, and `SuiteWorkspacePageController`.

## Slice 3: `SettingsStore` & `ReportStore` Lean & Performance Optimization (Findings #5, #6)
- **Files**:
  - `src/main/java/com/studio/store/SettingsStore.java`
  - `src/main/java/com/studio/store/ReportStore.java`
- **Changes**:
  - In `SettingsStore.load()`, only write to the H2 mirror (`saveToDatabase(prepareForStorage(s))`) when `hasDatabaseSettings()` is false, avoiding redundant AES-GCM encryption and DB write I/O on every read.
  - Replace duplicate `ObjectMapper` instances in `SettingsStore` and `ReportStore` with `ProjectStore.DEFAULT_MAPPER`.

## Slice 4: TDD Verification & Learnings
- **Files**:
  - `src/test/java/com/studio/service/AiAgentIndexingAndOptimizationTest.java`
  - `tasks/learnings.md`
- **Changes**:
  - Add unit test verifying granular `ProjectStore` updates sync to H2 (`updateCaseMeta`, `addAssertion`, `deleteGroup` cascade, `renameGroup` suite re-indexing, `ExcelReportService.export` & `HtmlReportService.export` archival in `ai_exports_index`).
  - Run `mvn test` and record learnings.
