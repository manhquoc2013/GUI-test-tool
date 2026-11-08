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

## Phase 10: 3-Pillar Enterprise Synthesis (Midscene + Skyvern + Testsigma + Automa)
- **Pillar 1: Multi-Locator Fallback Cascade & Action-Delta Healing (Testsigma + Skyvern)**
  - Create `com.studio.model.MultiLocatorProfile.java`: Ranked candidate locator strategies (ID, CSS, XPath, Role/Aria, Text).
  - Create `com.studio.runtime.MultiLocatorCascade.java`: Sub-15ms fast evaluation of fallback candidates before invoking AI/VLM.
  - Create `com.studio.runtime.ActionDeltaVerifier.java`: Skyvern-inspired state verification (URL transition, DOM mutation count, modal emergence) and phantom-click recovery.
  - Wire cascade and action-delta into `OptimizedSelfHealingExecutor.java`.
- **Pillar 2: Interactive Run Visualizer & Semantic AI Assertions (Midscene.js)**
  - Support `AssertionType.AI_SEMANTIC_ASSERT` in `Assertion.java` and code generation in `AssertCodeGenerator.java`.
  - Implement runtime evaluation in `StudioRuntime.semanticAssert(...)` with Fabric prompt template and OpenAiClient integration.
  - Update `HtmlReportService.java` to generate an interactive visualizer inspector tab with self-healing decision timeline and visual element bounding boxes.
- **Pillar 3: Reusable Step Groups & Test Data Profiles (Automa + Testsigma)**
  - Create `com.studio.model.StepGroup.java` and `com.studio.store.StepGroupStore.java` for reusable modular step sequences.
  - Create `com.studio.model.TestDataProfile.java` and `com.studio.util.VariableResolver.java` for data-driven testing and dynamic parameter interpolation (`{{var}}`, `${var}`).
  - Integrate Step Group expansion and Variable Resolution in `TestRunnerService.java` and `ProjectStore.java`.
- **Verification & TDD**:
  - Add comprehensive unit tests for each new component.
  - Run full test regression (`mvn clean test`) to verify all existing and new tests pass.

## Phase 11: Test Case Priority Classification & Targeted Execution (P0-P3)
- Create `com.studio.model.TestPriority.java` with P0, P1, P2, P3 definitions, labels, and parsing.
- Update `TestCase.java`, `AiGeneratedCase.java`, and `CaseResult.java` with `priority` field.
- Update `DatabaseManager.java` & `LocalDataIndexService.java` for H2 database schema and indexing.
- Update `AiTestGeneratorService.java` prompt and case mapping for autonomous priority evaluation.
- Update `TestRunnerService.java` with priority filtering support.
- Update `HtmlReportService.java` with priority badges and filter chips.
- Update `TestCaseExcelExportService.java` & `ExcelReportService.java` with priority column.
- Update `.agents/qa-planner.md` with priority guidelines.
- Add unit tests for `TestPriority`, targeted execution, and verify full test suite (`mvn clean test`).

## Phase 12: MarkItDown Universal Ingestion & Agency-Agents Specialist Roster
- Create `com.studio.service.MarkdownConverterEngine`:
  - Pure Java document to Markdown converter for `.docx`, `.xlsx`, `.pptx`, `.pdf`, `.csv`, `.json`, `.html`, `.txt`.
  - MarkItDown CLI / Python bridge adapter with seamless fallback.
  - Frontmatter metadata generation and token estimation.
- Enhance `DocumentParserService`:
  - Auto-convert non-image documents to Markdown.
  - Persist converted `.md` files to `data/converted_docs/<sanitized_name>_<timestamp>.md`.
  - Expose `getMarkdownPath()` and markdown preview access.
- Upgrade `com.studio.model.AiSkill`:
  - Integrate `UNIVERSAL_DOCUMENT_COMPILER` and `ACCESSIBILITY_AUDITOR` alongside upgraded `TEST_AUTOMATION_ENGINEER`, `FAILURE_RCA`, `SECURITY_FUZZING`, and `EXHAUSTIVE_UNIT_TEST`.
  - Incorporate strict rules from `agency-agents` (zero hard sleeps, role selectors, isolated test data, WCAG 2.1 AA checks).
  - Add slash commands `/doc`, `/compiler`, `/e2e`, `/playwright`, `/a11y`, `/rca`, `/fuzz`, `/unit`.
- UI & Controller Integration:
  - Add Markdown Preview Dialog (`MarkdownPreviewDialog`) in `com.studio.controller`.
  - Update `AiAssistantPageController` with Markdown preview button on file chips.
  - Update skill dropdown and quick prompt templates.
- Verification & TDD:
  - Write comprehensive tests for `MarkdownConverterEngineTest`.
  - Write tests for `AiSkill` and updated `DocumentParserService`.
  - Full suite regression with `mvn clean test`.

