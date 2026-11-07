# Plan: API Key Security & Transparent Storage Encryption

## Vertical Slices

1. **Slice 1: Cryptographic Engine (`ApiKeyCipher`)**
   - Implement `com.studio.util.ApiKeyCipher` with AES-256-GCM and PBKDF2WithHmacSHA256 device-bound entropy.
   - Support `encrypt(String plain)`, `decrypt(String cipherOrPlain)`, `isEncrypted(String str)`.
   - Write comprehensive unit tests in `ApiKeyCipherTest`.

2. **Slice 2: Storage Dual-Persistence & Export Protection (`SettingsStore`)**
   - Update `SettingsStore.save()` to write encrypted API keys to disk & H2 database.
   - Update `SettingsStore.load()` to transparently decrypt `enc:v1:...` envelopes.
   - Update `SettingsStore.exportSettings()` to mask API keys.
   - Update `SettingsStore.importSettings()` to preserve existing local keys when masked keys are imported.
   - Add unit tests in `SettingsSecurityTest`.

3. **Slice 3: Log & Terminal Output Sanitizer (`SecuritySanitizer`)**
   - Implement `com.studio.util.SecuritySanitizer` with pattern matching & active key redaction.
   - Hook into `TestRunnerService` terminal output and `OpenAiClient` error logging.
   - Add unit tests in `SecuritySanitizerTest`.

4. **Slice 4: Verification, Full Build & Git Checkpoint**
   - Run full unit test suite `mvn clean test`.
   - Verify UI dialogs still load and function cleanly without regressions.
   - Commit changes atomically.
