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
