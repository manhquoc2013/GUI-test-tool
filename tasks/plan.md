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

## Phase 8: System Review Hardening & Autonomous Optimizations
- **Self-Healing Selector Escaping**: Fix `OptimizedSelfHealingExecutor.persistMutation` to properly escape inner quotes in Java source literals and `newSelector` replacement.
- **Locator Chaining Self-Healing**: Wrap child `Locator` returns (`first()`, `last()`, `nth()`, `filter()`) in the healing dynamic proxy.
- **Data Cleanup Active-Run Guard**: Guard `DataCleanupService.executeCleanup` against executing while `RunCancelService.isRunActive()` is true, and disable cleanup button in `DataCleanupDialog`.
- **Machine Entropy Stability**: Improve `ApiKeyCipher.buildDeviceEntropy` to prioritize stable physical network interfaces (`en0`, `eth0`) and OS identifiers so dynamic virtual interfaces don't corrupt keys.
- **N+1 Database Query Optimization**: Batch load assertions in `ProjectDbService.searchCases` and `getCasesForSuite`.
- **Unified Browsers Path**: Route `PlaywrightUtil.resolveBrowsersPath()` through `StudioPaths.getBrowsersDir()`.
- **Vision Model Capability Check**: Skip Tier 3 VLM screenshot and image payload when non-vision/text-only models are configured.
- **Quiet I18n Resource Fallback**: Restrict missing bundle logging in `I18nService` to default language only.
- **Verification & Git Push**: Ensure all unit tests pass, commit atomically, and push to remote.

## Phase 9: Comprehensive Review Remediation & Architectural Optimizations
- **Task A (Must fix)**: Sanitize screenshot file names and paths in `TestRunnerService.java` against path separators (`/`, `\`) and illegal Windows characters (`:`, `*`, `?`, `"`, `<`, `>`, `|`) to ensure cross-platform test execution and screenshot evidence retention.
- **Task B (Must fix)**: Implement recursive project traversal in `LocalDataIndexService.java` to index all nested groups, suites, and cases into H2 cache on startup and resync.
- **Task C (Must fix)**: Redact API keys from `OpenAiClient.chatCompletion` error responses using `SecuritySanitizer.sanitize(err, apiKey)` before raising `IOException`.
- **Task D (Should fix)**: Implement atomic file writes for `.java` test scripts in `ProjectStore.java` and `OptimizedSelfHealingExecutor.java` to prevent script corruption upon unexpected shutdowns.
- **Task E (Should fix)**: Guard against `NullPointerException` and `InvalidPathException` when parsing screenshot file labels in `ExcelReportService.java`.
- **Task F (Nice to have)**: Centralize string, case name, and identifier sanitization into `StringEscapeUtil.java` and refactor duplicate sanitization logic.
- **Task G (Verification)**: Add comprehensive unit tests covering all 6 fixes, run full test regression suite, update documentation, and perform atomic commits.


## Phase 9: Multi-Agent AI Test Engineering, Self-Healing & TDD Verification (ECC)
- Implement multi-agent persona definitions in `.agents/`:
  - `qa-planner.md`: Spec mining, BVA, equivalence partitioning, TEST-ID generation.
  - `playwright-test-engineer.md`: Dual representation (NLP steps + test data + Playwright Java code).
  - `test-self-healer.md`: Failure capture, 4-phase diagnosis, anti-thrashing guard, locator/syntax healing.
  - `quality-auditor.md`: Behavioral coverage audit, assertion quality checker, silent failure detection.
- Implement specialized skills in `skills/`:
  - `skills/ai-test-suite-generator/SKILL.md`: Comprehensive test generation workflow from requirements or live DOM.
  - `skills/playwright-test-healer/SKILL.md`: Automated test execution error diagnosis and minimal-diff self-healing.
  - `skills/test-quality-gate/SKILL.md`: Verification loop and quality audit before saving tests to suites.
- Implement project rules in `rules/`:
  - `rules/playwright-java-testing.md`: Playwright Java coding standards, AAA pattern, locator resilience.
  - `rules/anti-thrashing-healing.md`: Anti-thrashing threshold (max 3 patches) and escalation protocol.
- Integrate guidelines into `AiTestGeneratorService.java` prompt & documentation.
- Verify full regression test suite (`mvn clean test`) to ensure zero regressions.
