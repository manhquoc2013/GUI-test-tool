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

