# Implementation Plan: System-Wide Review Round 4 Fixes & Optimizations

## Slice 1: `AiCasePromptDialog` Single-Case AI Persistence (Finding #1)
- **Files**:
  - `src/main/java/com/studio/controller/AiCasePromptDialog.java`
- **Changes**:
  - In `applyBtn.setOnAction`, persist `gen.getTestSteps()`, `gen.getTestDataSets()`, and `gen.getPriority()` using `projectStore.updateCaseStepsAndData(...)` and `projectStore.updateCasePriority(...)`, and update `activeCase` in memory (`setTestSteps`, `setTestDataSets`, `setPriority`, `setStartUrl`, `setStatus`).

## Slice 2: Full Assertion Type Catalog, `Assertion.getEffectiveValue()`, UI & H2 Sync (Finding #2)
- **Files**:
  - `src/main/java/com/studio/model/Assertion.java`
  - `src/main/java/com/studio/util/AssertTypeDisplay.java`
  - `src/main/java/com/studio/service/AssertCodeGenerator.java`
  - `src/main/java/com/studio/controller/AssertBuilderPopupController.java`
  - `src/main/java/com/studio/controller/SuiteWorkspacePageController.java`
  - `src/main/java/com/studio/db/LocalDataIndexService.java`
- **Changes**:
  - Add `getEffectiveValue()`, `resolveAttribute()`, and `resolveExpectedValue()` helpers on `Assertion` so two-parameter assertions (`ATTRIBUTE_EQUALS`, `CSS_CONTAINS`, `CSS_PROPERTY_EQUALS`, `LOCAL_STORAGE_EQUALS`, `SESSION_STORAGE_EQUALS`, `COOKIE_VALUE_EQUALS`, `ATTRIBUTE_NOT_EQUALS`) work seamlessly whether populated via `attribute` + `expectedValue` (AI) or `value` (`key=expected` in UI).
  - Expand `AssertTypeDisplay.ALL_TYPES` and `AssertTypeDisplay.label(type)` to cover all advanced assertion types (`PAGE_NOT_CONTAINS_TEXT`, `URL_NOT_CONTAINS`, `URL_NOT_EQUALS`, `TITLE_NOT_CONTAINS`, `ELEMENT_FOCUSED`, `ELEMENT_READONLY`, `ELEMENT_IN_VIEWPORT`, `ELEMENT_BBOX_VISIBLE`, `ATTRIBUTE_NOT_EQUALS`, `ATTRIBUTE_EXISTS`, `CSS_PROPERTY_EQUALS`, `IMAGE_BROKEN`, `SELECT_SELECTED_TEXT`, `COOKIE_VALUE_EQUALS`, `LOCAL_STORAGE_EXISTS`, `SESSION_STORAGE_EXISTS`, `DIALOG_ALERT_TEXT_CONTAINS`, `API_STATUS_EQUALS`, `PAGE_LOAD_TIME_MAX`, `TOAST_VISIBLE`, `MODAL_VISIBLE`, `MODAL_HIDDEN`, `BREADCRUMB_CONTAINS`, `BADGE_COUNT_EQUALS`, `AI_SEMANTIC_ASSERT`).
  - Support these advanced assertion types in `AssertCodeGenerator.isSupported` and `generateAssertCode`.
  - Update `AssertBuilderPopupController`, `SuiteWorkspacePageController`, and `LocalDataIndexService.indexCaseInternal` to use `a.getEffectiveValue()`.

## Slice 3: Safe Script File Path Resolution & Recursive Group Rename H2 Sync + Shared Mapper (Findings #3, #4, #5)
- **Files**:
  - `src/main/java/com/studio/controller/SuiteWorkspacePageController.java`
  - `src/main/java/com/studio/controller/ExplorerPageController.java`
  - `src/main/java/com/studio/store/ProjectStore.java`
  - `src/main/java/com/studio/db/LocalDataIndexService.java`
  - `src/main/java/com/studio/store/AiChatHistoryStore.java`
- **Changes**:
  - In `SuiteWorkspacePageController.runCodegenBlocking` & `runCaseTarget` and `ExplorerPageController.findCaseScriptFile` & `runSelected`, check `scriptFile != null && !scriptFile.isBlank()` and fall back to `StringEscapeUtil.safeFileName(caseName, "case") + ".java"`.
  - Make `LocalDataIndexService.syncGroupDirectory(Path groupDir, String groupName)` `public` and invoke it in `ProjectStore.renameGroup` so nested sub-groups and child suites are recursively re-indexed in H2.
  - Reuse `ProjectStore.DEFAULT_MAPPER` in `LocalDataIndexService` and `AiChatHistoryStore`.

## Slice 4: Regression Unit Tests & Learnings
- **Files**:
  - `src/test/java/com/studio/service/AiAgentIndexingAndOptimizationTest.java`
  - `tasks/learnings.md`
