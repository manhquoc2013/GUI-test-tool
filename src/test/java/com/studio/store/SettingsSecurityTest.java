package com.studio.store;

import com.studio.util.ApiKeyCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsSecurityTest {

  @Test
  void testApiKeyEncryptedAtRestOnDiskAndDatabase(@TempDir Path tempDir) throws IOException {
    SettingsStore store = new SettingsStore(tempDir);
    SettingsStore.Settings s = new SettingsStore.Settings();
    String plainSecret = "sk-proj-super-secret-production-key-998877";
    s.getAiConfig().setApiKey(plainSecret);

    store.save(s);

    // 1. Verify on-disk file contains encrypted envelope, NEVER plaintext
    Path settingsJson = tempDir.resolve("settings.json");
    assertTrue(Files.exists(settingsJson));
    String rawJson = Files.readString(settingsJson);
    assertFalse(rawJson.contains(plainSecret), "settings.json must NEVER store plaintext API key on disk!");
    assertTrue(rawJson.contains(ApiKeyCipher.ENVELOPE_PREFIX), "settings.json must contain enc:v1: envelope!");

    // 2. Verify load transparently decrypts the key in memory
    SettingsStore.Settings reloaded = store.load();
    assertEquals(plainSecret, reloaded.getAiConfig().getApiKey(),
        "Loaded settings must transparently yield decrypted API key in memory");
    assertEquals(plainSecret, reloaded.getAiConfig().getEffectiveApiKey());
  }

  @Test
  void testLegacyPlaintextKeyMigration(@TempDir Path tempDir) throws IOException {
    Path settingsJson = tempDir.resolve("settings.json");
    String legacyJson = """
        {
          "browser" : "CHROMIUM",
          "aiConfig" : {
            "apiKey" : "sk-proj-legacy-plaintext-key-112233",
            "model" : "gpt-4o"
          }
        }
        """;
    Files.writeString(settingsJson, legacyJson);

    SettingsStore store = new SettingsStore(tempDir);
    SettingsStore.Settings loaded = store.load();

    assertEquals("sk-proj-legacy-plaintext-key-112233", loaded.getAiConfig().getApiKey());

    // Re-saving must automatically migrate legacy plaintext to encrypted envelope
    store.save(loaded);
    String reSavedJson = Files.readString(settingsJson);
    assertFalse(reSavedJson.contains("sk-proj-legacy-plaintext-key-112233"));
    assertTrue(reSavedJson.contains(ApiKeyCipher.ENVELOPE_PREFIX));
  }

  @Test
  void testExportMasksApiKeyAndImportPreservesExistingKey(@TempDir Path tempDir) throws IOException {
    SettingsStore store = new SettingsStore(tempDir);
    SettingsStore.Settings s = new SettingsStore.Settings();
    String secretKey = "sk-proj-1234567890abcdefghijSECRET";
    s.getAiConfig().setApiKey(secretKey);
    store.save(s);

    // 1. Export settings
    Path exportFile = tempDir.resolve("exported_settings.json");
    store.exportSettings(exportFile);

    assertTrue(Files.exists(exportFile));
    String exportedContent = Files.readString(exportFile);
    assertFalse(exportedContent.contains(secretKey), "Exported settings must not contain plaintext API key");
    assertTrue(exportedContent.contains("•"), "Exported API key must be masked with bullets");
    assertTrue(exportedContent.contains("CRET"), "Exported API key can retain suffix for reference");

    // 2. Import into another store with an existing key: existing key must NOT be
    // overwritten by masked placeholder
    Path targetDir = tempDir.resolve("target_store");
    SettingsStore targetStore = new SettingsStore(targetDir);
    SettingsStore.Settings targetSettings = new SettingsStore.Settings();
    String targetExistingKey = "sk-proj-existing-local-key-in-target";
    targetSettings.getAiConfig().setApiKey(targetExistingKey);
    targetStore.save(targetSettings);

    // Import the exported backup into targetStore
    targetStore.importSettings(exportFile);

    SettingsStore.Settings targetReloaded = targetStore.load();
    assertEquals(targetExistingKey, targetReloaded.getAiConfig().getApiKey(),
        "Importing a masked backup must NOT overwrite existing local key with masked placeholder");
  }
}
