# Tasks: Security Hardening, Deterministic Storage Encryption & Review Fixes

- [x] Task 1: Restore `ApiKeyCipher.java` and implement deterministic interface sorting for machine entropy <!-- id: 11 -->
- [x] Task 2: Fix unmasked backup export roundtrip in `SettingsDualPersistenceTest.java` <!-- id: 12 -->
- [x] Task 3: Enforce proper error logging in `SettingsStore` instead of swallowed empty catch blocks <!-- id: 13 -->
- [x] Task 4: Re-enable `SecuritySanitizer.sanitize(...)` in `OpenAiClient.java` and `TestRunnerService.java` <!-- id: 14 -->
- [x] Task 5: Clean up `AiConfig.java` to prevent redundant decryption on plaintext env vars <!-- id: 15 -->
- [x] Task 6: Run full verification suite (`mvn clean test`) with 138/138 tests passing <!-- id: 16 -->
- [x] Task 7: Implement Auto-Scan URL in `AiAssistantPageController` and `AiCasePromptDialog` when prompt or suite has HTTP/HTTPS link <!-- id: 17 -->
- [x] Task 8: Upgrade `SYSTEM_PROMPT` in `AiTestGeneratorService` to enforce mandatory login/authentication handling before inner business flows <!-- id: 18 -->
- [x] Task 9: Add `extractFirstUrl` unit tests and verify suite (139/139 tests pass) <!-- id: 19 -->
- [x] Task 10: Sanitize Java class identifier in `ProjectStore.createCase` for spaces & keywords <!-- id: 20 -->
- [x] Task 11: Fix class replacement regex and add auto dialog handling in `ScriptHarnessTransformer` <!-- id: 21 -->
- [x] Task 12: Upgrade `case 2.java` and populate `testSteps` & `testDataSets` in `data/project/test 1/test 1/suite.json` <!-- id: 22 -->
- [x] Task 13: Add unit tests for spaced/invalid class name handling in `ScriptHarnessTransformerTest` and `ProjectStoreTest` <!-- id: 23 -->
- [x] Task 14: Run full verification (`mvn clean test`) and update `tasks/learnings.md` <!-- id: 24 -->
- [x] Task 15: Create `com.studio.model.AiSkill` with Vietnamese names, Fabric prompts & heuristic detection <!-- id: 25 -->
- [x] Task 16: Integrate `AiSkill` into `AiTestGeneratorService` (Skill Routing & multi-mode responses) <!-- id: 26 -->
- [x] Task 17: Update `ai_assistant_page.fxml` and `AiAssistantPageController` with Skill dropdown & slash commands <!-- id: 27 -->
- [x] Task 18: Add CSS styles for Skill selector in `studio.css` and `studio-light.css` <!-- id: 28 -->
- [x] Task 19: Write unit tests in `AiSkillTest` and verify entire test suite (`mvn clean test`) <!-- id: 29 -->
- [x] Task 20: Create `com.studio.model.ExportLanguage` with Vietnamese (default) & English <!-- id: 30 -->
- [x] Task 21: Add language support to `TestCaseExcelExportService`, `ExcelReportService` & `HtmlReportService` <!-- id: 31 -->
- [x] Task 22: Create `com.studio.controller.ExportLanguageDialog` with modal picker (defaulting to Tiếng Việt) <!-- id: 32 -->
- [x] Task 23: Wire language selection into `SuiteWorkspacePageController`, `ExplorerPageController`, `ReportsPageController`, and `ReportDetailPageController` <!-- id: 33 -->
- [x] Task 24: Add unit tests for bilingual exports in `TestCaseExcelExportServiceTest`, `HtmlReportServiceTest` & verify suite <!-- id: 34 -->
- [x] Task 25: Implement atomic file write protection (`writeJsonAtomic`) in `ProjectStore` and `ReportStore` <!-- id: 35 -->
- [x] Task 26: Add in-memory LRU cache with TTL in `LiveDomScanner` for repeated URL scanning <!-- id: 36 -->
- [x] Task 27: Implement Excel 32,767 cell limit safe truncation guard in `TestCaseExcelExportService` & `ExcelReportService` <!-- id: 37 -->
- [x] Task 28: Add unit tests for atomic writes, DOM cache, and cell truncation; verify full test suite (154/154 passing) <!-- id: 38 -->
- [x] Task 29: Enhance `StudioPaths.java` for installed permission safety & data persistence <!-- id: 39 -->
- [x] Task 30: Write unit tests for `StudioPaths` in `StudioPathsTest.java` and verify all tests pass <!-- id: 40 -->
- [x] Task 31: Create `scripts/build-installer.sh` for macOS `.dmg` installer with bundled offline runtime <!-- id: 41 -->
- [x] Task 32: Create `scripts/installer.iss` and `scripts/build-installer.bat` for Windows `.exe` Setup <!-- id: 42 -->
- [x] Task 33: Create `.github/workflows/build-installers.yml` for automated multi-OS release builds <!-- id: 43 -->
- [x] Task 34: Execute macOS `.dmg` build, verify installer artifact & offline browser integration <!-- id: 44 -->
- [x] Task 35: Run full verification suite and update `tasks/learnings.md` <!-- id: 45 -->
- [x] Task 36: Fix selector escaping and locator chaining proxy in `OptimizedSelfHealingExecutor` <!-- id: 46 -->
- [x] Task 37: Add run-active guard in `DataCleanupService` and `DataCleanupDialog` <!-- id: 47 -->
- [x] Task 38: Stabilize machine entropy derivation in `ApiKeyCipher` <!-- id: 48 -->
- [x] Task 39: Eliminate N+1 assertion queries in `ProjectDbService` <!-- id: 49 -->
- [x] Task 40: Unify browser path resolution in `PlaywrightUtil` with `StudioPaths.getBrowsersDir()` <!-- id: 50 -->
- [x] Task 41: Add vision model capability check in `OptimizedSelfHealingExecutor` and quiet i18n logging <!-- id: 51 -->
- [x] Task 42: Comprehensive unit tests and full suite regression verification <!-- id: 52 -->
- [x] Task 43: Update `tasks/learnings.md`, atomic commit and git push <!-- id: 53 -->
- [x] Task 44: Centralize filename & identifier sanitization in `StringEscapeUtil` <!-- id: 54 -->
- [x] Task 45: Sanitize screenshot file names & paths in `TestRunnerService` <!-- id: 55 -->
- [x] Task 46: Recursive nested group & suite indexing in `LocalDataIndexService` <!-- id: 56 -->
- [x] Task 47: Sanitize API Key in `OpenAiClient.chatCompletion` error responses <!-- id: 57 -->
- [x] Task 48: Atomic file writes for `.java` test scripts in `ProjectStore` and `OptimizedSelfHealingExecutor` <!-- id: 58 -->
- [x] Task 49: Null and path safety for screenshot link labels in `ExcelReportService` <!-- id: 59 -->
- [x] Task 50: Add targeted unit tests for all remediations & verify full suite <!-- id: 60 -->

- [x] Task 51: Create Multi-Agent Personas in `.agents/` (`qa-planner.md`, `playwright-test-engineer.md`, `test-self-healer.md`, `quality-auditor.md`) <!-- id: 61 -->
- [x] Task 52: Create Skills in `skills/` (`ai-test-suite-generator`, `playwright-test-healer`, `test-quality-gate`) <!-- id: 62 -->
- [x] Task 53: Create Rules in `rules/` (`playwright-java-testing.md`, `anti-thrashing-healing.md`) <!-- id: 63 -->
- [x] Task 54: Optimize `AiTestGeneratorService.java` prompt with ECC-inspired BVA, dual representation & anti-swallow assertions <!-- id: 64 -->
- [x] Task 55: Run full test suite regression (`mvn clean test`) and update `tasks/learnings.md` <!-- id: 65 -->

- [x] Task 56: Fix unclosed `Files.walk` Streams in `ProjectStore` and `TestRunnerService` <!-- id: 66 -->
- [x] Task 57: Implement Anti-Thrashing Guard in `OptimizedSelfHealingExecutor` <!-- id: 67 -->
- [x] Task 58: Optimize `DatabaseManager` with Safe Connection Reusability & Validation <!-- id: 68 -->
- [x] Task 59: Harden Cryptographic Entropy Fallback in `ApiKeyCipher` <!-- id: 69 -->
- [x] Task 60: Optimize `LiveDomScanner` with Reusable Headless Browser Session <!-- id: 70 -->
- [x] Task 61: Optimize `LocalDataIndexService.syncAllFromDisk` with MTime Caching <!-- id: 71 -->
- [x] Task 62: Add targeted unit tests and verify full suite (`mvn clean test`) <!-- id: 72 -->
- [x] Task 63: Update `tasks/learnings.md` and make atomic commits <!-- id: 73 -->

- [x] Task 64: Create `MultiLocatorProfile.java` and `MultiLocatorCascade.java` (Testsigma fallback cascade) <!-- id: 74 -->
- [x] Task 65: Create `ActionDeltaVerifier.java` (Skyvern post-action state delta check & recovery) <!-- id: 75 -->
- [x] Task 66: Integrate `MultiLocatorCascade` and `ActionDeltaVerifier` into `OptimizedSelfHealingExecutor.java` <!-- id: 76 -->
- [x] Task 67: Add `AI_SEMANTIC_ASSERT` to `Assertion.java` and `AssertCodeGenerator.java` <!-- id: 77 -->
- [x] Task 68: Implement `StudioRuntime.semanticAssert(...)` with Fabric prompt template <!-- id: 78 -->
- [x] Task 69: Upgrade `HtmlReportService.java` with interactive Visualizer Inspector (Midscene dump.html style) <!-- id: 79 -->
- [x] Task 70: Create `StepGroup.java` model and `StepGroupStore.java` for reusable test step macros (Testsigma) <!-- id: 80 -->
- [x] Task 71: Create `TestDataProfile.java` and `VariableResolver.java` for dynamic parameter injection (Automa) <!-- id: 81 -->
- [x] Task 72: Integrate Step Group macro expansion and Variable Resolution in `TestRunnerService.java` <!-- id: 82 -->
- [x] Task 73: Write comprehensive unit tests for all components and verify full test suite (`mvn clean test`) <!-- id: 83 -->
- [x] Task 74: Update `tasks/learnings.md` and commit atomic changes <!-- id: 84 -->

- [x] Task 75: Create `TestPriority.java` and update `TestCase`, `AiGeneratedCase`, `CaseResult` <!-- id: 85 -->
- [x] Task 76: Update `DatabaseManager` and `LocalDataIndexService` for H2 priority schema & indexing <!-- id: 86 -->
- [x] Task 77: Update `AiTestGeneratorService` & `.agents/qa-planner.md` for autonomous priority evaluation <!-- id: 87 -->
- [x] Task 78: Update `TestRunnerService` with priority filtering support <!-- id: 88 -->
- [x] Task 79: Update `HtmlReportService` with priority badges and filter chips <!-- id: 89 -->
- [x] Task 80: Update `TestCaseExcelExportService` & `ExcelReportService` with priority column <!-- id: 90 -->
- [x] Task 81: Add comprehensive unit tests and run full verification suite (`mvn clean test`) <!-- id: 91 -->
- [x] Task 82: Update `tasks/learnings.md` and commit atomic changes <!-- id: 92 -->

- [x] Task 83: Create `TestDataProfileStore.java` for persistent test data sets and variable profiles <!-- id: 93 -->
- [x] Task 84: Bind `testDataProfileId` in `TestSuite`, `TestCase`, and wire variable resolution in `TestRunnerService` <!-- id: 94 -->
- [x] Task 85: Implement dynamic Multi-Locator Profile promotion on successful self-healing <!-- id: 95 -->
- [x] Task 86: Harden browser process cleanup and temporary profile reclamation in `PlaywrightUtil` & `ResourceReleaseService` <!-- id: 96 -->
- [x] Task 87: Add comprehensive unit tests for `TestDataProfileStore` & runner integration, verify 100% green test suite <!-- id: 97 -->
- [x] Task 88: Update `SPEC.md`, `tasks/learnings.md`, and commit changes <!-- id: 98 -->

- [x] Task 89: Create `MarkdownConverterEngine.java` for high-fidelity conversion of docx, xlsx, pptx, pdf, html, csv, json to Markdown AST with YAML frontmatter <!-- id: 99 -->
- [x] Task 90: Enhance `DocumentParserService.java` to auto-convert uploaded documents to Markdown and persist into `data/converted_docs/` <!-- id: 100 -->
- [x] Task 91: Upgrade `AiSkill.java` with Agency-Agents specialist roster (Universal Document Compiler, Test Automation Engineer, Accessibility Auditor, RCA & Flake Eliminator) and strict testing rules <!-- id: 101 -->
- [x] Task 92: Update `AiAssistantPageController.java` with Markdown preview button on file chips and new Agent skills <!-- id: 102 -->
- [x] Task 93: Create `MarkdownPreviewDialog.java` for viewing and inspecting converted Markdown AST <!-- id: 103 -->
- [x] Task 94: Write comprehensive unit tests for `MarkdownConverterEngineTest`, `DocumentParserServiceTest`, and `AiSkillTest` <!-- id: 104 -->
- [x] Task 95: Run full test suite regression (`mvn clean test`), update `tasks/learnings.md`, and atomic git commit <!-- id: 105 -->

- [x] Task 96: Fix CLI process leak, buffer deadlock & cache CLI availability in `MarkdownConverterEngine` <!-- id: 106 -->
- [x] Task 97: Eliminate static Playwright thread affinity crash in `LiveDomScanner` <!-- id: 107 -->
- [x] Task 98: Preserve sequential document interleaving (BodyElements) & complete text in `MarkdownConverterEngine.convertDocx` <!-- id: 108 -->
- [x] Task 99: Add auto-commit reset and transaction rollback guard in `DatabaseManager` connection pool <!-- id: 109 -->
- [x] Task 100: Strip UTF-8 BOM (`\uFEFF`) from CSV/TSV table headers in `MarkdownConverterEngine` <!-- id: 110 -->
- [x] Task 101: Include `data/converted_docs/` in `DataCleanupService` disk usage and cleanup execution <!-- id: 111 -->
- [x] Task 102: Write comprehensive unit tests for all remediations & verify 100% green test suite <!-- id: 112 -->
- [x] Task 103: Update `tasks/learnings.md`, `tasks/plan.md`, and make atomic commit <!-- id: 113 -->

- [x] Task 104: Upgrade `TestRunnerService.buildReproductionLog` with clear error classification, target locator extraction, clean messages, and reproduction steps <!-- id: 114 -->
- [x] Task 105: Eliminate raw application/framework stack trace dump (`📋 VẾT STACK TRACE ĐẦY ĐỦ`) from test failure reports <!-- id: 115 -->
- [x] Task 106: Enhance test step reproduction sequences using `TestCase.getTestSteps()` in `buildReproductionLog` <!-- id: 116 -->
- [x] Task 107: Update and expand unit tests in `TestRunnerScreenshotFinalizeTest` to verify clean reports without stack traces <!-- id: 117 -->
- [x] Task 108: Update `.agents/test-self-healer.md`, `rules/anti-thrashing-healing.md`, `tasks/learnings.md`, and verify full regression suite (`mvn clean test`) <!-- id: 118 -->

- [x] Task 109: Enforce inactive suite & inactive test case execution guards in `ProjectStore` (`collectSuiteCases`, `enumerateSuiteCases`, `collectSingleCase`) <!-- id: 119 -->
- [x] Task 110: Add execution pre-checks and dynamic run button visibility in `ExplorerPageController` and `SuiteWorkspacePageController` <!-- id: 120 -->
- [x] Task 111: Implement universal delete buttons for Group, Suite, and Case in Explorer toolbar, TreeCell inline actions, and Preview panel <!-- id: 121 -->
- [x] Task 112: Implement delete buttons for Case and Suite in Suite Workspace (case row, context menu, and suite info panel) <!-- id: 122 -->
- [x] Task 123: Add unit tests for inactive execution skip logic and deletion buttons; verify full test suite regression <!-- id: 123 -->
- [x] Task 124: Update `tasks/learnings.md`, atomic commit and documentation <!-- id: 124 -->




## Phase 12: Interactive Clarification, Conversational Intelligence & Deep Test Generation
- [ ] 12.1 Create `AiClarificationQuestion.java` model and update `AiChatMessage.java` with clarification & intent properties
- [ ] 12.2 Create `AiProjectAdvisorService.java` for test case search, quality audit (Agency-Agents checklist), and gap analysis
- [ ] 12.3 Create `AiAmbiguityAnalyzer.java` and upgrade `AiTestGeneratorService.java` (SYSTEM_PROMPT, multi-scenario generation, intent routing)
- [ ] 12.4 Update `AiAssistantPageController.java` to render interactive clarification card in chat, handle user answers, and support conversational chatbot queries
- [ ] 12.5 Add CSS styling in `studio.css` for clarification cards, option chips, and interactive answer controls
- [ ] 12.6 Add unit and integration tests (`AiAmbiguityAnalyzerTest`, `AiProjectAdvisorServiceTest`, `AiClarificationFlowTest`)
- [ ] 12.7 Run full test suite (`mvn test`) and verify 0 failures / 0 regressions
