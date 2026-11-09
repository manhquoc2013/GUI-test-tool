# Implementation Plan: Auto-Create Group and Suite on AI Case Save

## Overview
Allow users to immediately save ("Nhập vào Suite") AI-generated test cases even when no group or suite has been created or selected yet, by automatically creating a standard group (e.g. `AI-Tests`) and a descriptive suite (derived from the chat session topic or `AI-Generated-Suite`).

---

## Slices

### Slice 1: Auto-Creation Logic in `AiAssistantPageController.java`
- In `applyCasesToSelectedSuite()`:
  - Remove blocking check that requires `group` and `suite` to be pre-selected.
  - If `group` is not selected or empty:
    - If `targetGroupCombo` has existing items, use the selected one or first one.
    - Otherwise, auto-assign default group name `"AI-Tests"`.
  - If `suite` is not selected or empty:
    - If `targetSuiteCombo` has existing items under the group, use the selected one or first one.
    - Otherwise, auto-assign a clean descriptive name via `resolveAutoSuiteName()` (derived from `currentSession.getTitle()` or `"AI-Generated-Suite"`).
  - Ensure parent directory, group directory with `group.json`, and suite directory with `suite.json` are created on disk.
  - Update `targetGroupCombo` and `targetSuiteCombo` items and selection to reflect the newly created group and suite.
  - Proceed with `applyCasesToSuite`, snapshot creation, and workspace opening.
  - Notify user with informative Toast message.

### Slice 2: Integration & Prompt Context Improvement
- In `executePromptInternal()`:
  - If target group or suite is not yet selected, provide clean friendly placeholder text in AI system prompt context instead of `"Nhóm đích: null, Suite đích: null"`.
- If `ExplorerPageController` is loaded, invoke `reloadFromDisk()` so the new group and suite appear immediately in the explorer tree.

### Slice 3: Regression Unit Testing & Verification
- Add unit test in `src/test/java/com/studio/controller/AiAssistantAutoCreateSuiteTest.java`:
  - Test saving cases when project has 0 groups and 0 suites:
    - Verify group and suite folders and json files (`group.json`, `suite.json`) are automatically created.
    - Verify test cases (`.java` and in `suite.json`) are successfully written.
    - Verify ComboBoxes in AI Assistant are updated with the new group and suite.
- Run `mvn clean test` across the entire project (must pass 300+ tests).
- Update `tasks/learnings.md`.
