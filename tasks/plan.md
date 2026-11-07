# Implementation Plan: AI Agent Test Generation & Automation Optimization

## Phase 1: Models & Data Structures
- Create `com.studio.model.TestStep.java` (stepNumber, action, target, testData, description, expectedResult).
- Create `com.studio.model.TestDataItem.java` (scenario, parameters, expectedOutcome).
- Enhance `com.studio.model.AiGeneratedCase.java` with testSteps, testDataSets, preconditions.
- Enhance `com.studio.model.TestCase.java` with testSteps, testDataSets.
- Unit tests for model serialization and Jackson mappings.

## Phase 2: Live DOM Scanner & Token Optimization
- Create/Enhance DOM representation formatting in `com.studio.service.AiTokenOptimizer.java` (compact element table format).
- Create `com.studio.service.LiveDomScanner.java` to extract interactive DOM from `SharedBrowserSession` or URL via headless Playwright page.
- Unit tests for DOM formatting and token estimation.

## Phase 3: Multi-Stage AI Generator & Validator Engine
- Update `AiTestGeneratorService.java`:
  - Upgrade `SYSTEM_PROMPT` to enforce dual representation (NLP testSteps + Test Data + Playwright Java code with `StudioRuntime.healLocator(...)` and wait patterns).
  - Add `generateTestCasesWithDom(config, userPrompt, attachments, contextInfo, domJson, history)` overload.
  - Implement `ScriptValidator` / auto-fixer for Playwright Java template integrity.
  - Update `applyCasesToSuite(...)` to propagate `testSteps` and `testDataSets` into `TestCase`.
- Unit tests for `AiTestGeneratorService` with mock JSON containing steps, data sets, and scripts.

## Phase 4: UI & Controller Integration
- Update `ai_assistant_page.fxml` to add "Các bước kiểm thử (Steps)" tab, "Dữ liệu kiểm thử (Test Data)" tab, and "Quét DOM (Live Scan)" action.
- Update `AiAssistantPageController.java` to bind new tabs, handle Live DOM scanning, and display steps and test data.
- Add CSS styling for steps list and test data table in `studio.css`.

## Phase 5: Verification & Full Regression
- Run unit test suite: `mvn test`.
- Verify zero regressions across all existing features.
- Update `tasks/learnings.md` and summarize results.

## Phase 6: Security Hardening & Deterministic Storage Encryption
- Restore and stabilize `ApiKeyCipher.java` with deterministic network interface sorting for entropy stability.
- Safeguard `SettingsStore.java` with non-destructive fallback if decryption fails, avoiding wiping out database mirrors.
- Add error logging for database and disk storage operations in `SettingsStore`.
- Protect log and terminal outputs via `SecuritySanitizer.sanitize(...)` in `OpenAiClient` and `TestRunnerService`.
- Verify all 138 unit tests pass cleanly.

## Phase 7: Full Offline Native Installers (macOS .dmg & Windows .exe Setup)
- Update `StudioPaths.java` to support installed app permission safety (redirecting writable data to user app support directory when installed in system directories like `/Applications` or `Program Files`, while preserving static bundled `browsers/` and `tools/` from `APP_HOME`).
- Add unit tests for `StudioPaths` in `StudioPathsTest.java`.
- Create `scripts/build-installer.sh` to package macOS `.app` bundle and build `.dmg` drag-and-drop installer.
- Create `scripts/installer.iss` and `scripts/build-installer.bat` for Windows Inno Setup / jpackage installer.
- Create GitHub Actions workflow `.github/workflows/build-installers.yml` for automated CI/CD installer generation.
- Execute macOS `.dmg` installer build and verify integrity, launchability, and offline Chromium presence.
