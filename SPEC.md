# Technical Specification: API Key Security & Transparent Storage Encryption

## 1. Objectives & Overview
Implement enterprise-grade security for AI API keys (OpenAI, Gemini, OpenRouter, Groq, DeepSeek, etc.) across Playwright Automation Studio:
1. **Encryption at Rest**: Store all API keys in `settings.json` and H2 database using authenticated AES-256-GCM encryption with machine-bound key derivation (PBKDF2WithHmacSHA256).
2. **Transparent Backward Compatibility**: Seamlessly detect legacy plaintext keys (`sk-...`), automatically migrate and re-encrypt on the next save without user friction.
3. **Leak-Proof Export & Import**: Automatically sanitize / mask API keys (`sk-***`) when exporting settings to JSON for sharing; protect existing credentials from being overwritten by masked placeholders on import.
4. **Execution & Log Sanitization**: Detect and redact API keys from test execution logs, terminal outputs, error messages, and reports.

## 2. Core Architecture & Enhancements

### 2.1 Cryptographic Service (`com.studio.util.ApiKeyCipher`)
- **Algorithm**: `AES/GCM/NoPadding` (256-bit key, 128-bit authentication tag, 12-byte cryptographic random IV per encryption).
- **Key Derivation**: `PBKDF2WithHmacSHA256` (65,536 iterations, 256-bit output).
- **Entropy Sources**: Machine-bound identifiers (OS username, user home, OS name, network MAC/hardware hash) mixed with an internal application salt.
- **Envelope Format**: `enc:v1:<base64(iv + ciphertext_with_tag)>`.
- **Validation**:
  - GCM authentication tag protects against unauthorized modification/tampering.
  - Fail-safe fallback: If decryption fails (e.g. copied to a different machine), clear or prompt safely without crashing.

### 2.2 Storage & Dual-Persistence Layer (`com.studio.store.SettingsStore`)
- On `save(Settings s)`:
  - Deep-clone or transform `Settings` so on-disk `settings.json` and database `system_settings` only persist the encrypted envelope `enc:v1:...`.
  - In-memory `Settings` remains ready for runtime execution.
- On `load()`:
  - Detect `enc:v1:` prefix -> decrypt transparently.
  - Detect plaintext key -> keep in memory, will automatically be encrypted on the next save cycle.
- On `exportSettings(Path targetFile)`:
  - Mask the API key (e.g. `sk-••••••` or empty) so backup files are safe to share with teammates.
- On `importSettings(Path sourceFile)`:
  - If imported key is masked / contains `•••` or `***`, keep the current existing local key intact.

### 2.3 Output & Log Sanitizer (`com.studio.util.SecuritySanitizer`)
- Detect API key patterns (`sk-[A-Za-z0-9_-]{20,}`, `Bearer ...`) and active in-memory API keys.
- Redact to `sk-...[last 4 chars]` across `TestRunnerService`, `StatusBarService`, and terminal callbacks.

## 3. Testing Strategy
- `ApiKeyCipherTest`: Encryption/decryption roundtrip, random IV uniqueness, tampering rejection, legacy key support.
- `SettingsSecurityTest`: Verify `settings.json` never contains plaintext API keys on disk.
- `SettingsExportSecurityTest`: Verify export file masking and safe import behavior.
- `LogSanitizerTest`: Verify redaction of keys in terminal logs and messages.
