package com.studio.store;

import com.studio.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsDualPersistenceTest {

    private DatabaseManager dbManager;

    @BeforeEach
    public void setup() {
        dbManager = DatabaseManager.createInMemory("settings_test_" + System.currentTimeMillis());
        DatabaseManager.setInstance(dbManager);
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        DatabaseManager.setInstance(null);
    }

    @Test
    public void testDualPersistenceAndDatabaseRecovery(@TempDir Path tempDir) throws IOException {
        SettingsStore store = new SettingsStore(tempDir);

        SettingsStore.Settings s = new SettingsStore.Settings();
        s.setBrowser("FIREFOX");
        s.setTheme("light");
        s.getAiConfig().setApiKey("sk-secret-test-key");
        s.getAiConfig().setModel("gpt-4o");

        // Save
        store.save(s);

        Path diskFile = tempDir.resolve("settings.json");
        assertTrue(Files.exists(diskFile), "Disk file must exist");

        // Now simulate accidental disk file deletion or corruption
        Files.deleteIfExists(diskFile);
        assertFalse(Files.exists(diskFile));

        // Load must recover from H2 database
        SettingsStore.Settings recovered = store.load();
        assertNotNull(recovered);
        assertEquals("FIREFOX", recovered.getBrowser());
        assertEquals("light", recovered.getTheme());
        assertEquals("sk-secret-test-key", recovered.getAiConfig().getApiKey());
        assertEquals("gpt-4o", recovered.getAiConfig().getModel());

        // And disk file must have been automatically restored!
        assertTrue(Files.exists(diskFile), "Disk file should be auto-restored from H2 mirror");
    }

    @Test
    public void testExportAndImportSettings(@TempDir Path tempDir) throws IOException {
        SettingsStore store = new SettingsStore(tempDir);

        SettingsStore.Settings s = new SettingsStore.Settings();
        s.setBrowser("WEBKIT");
        s.setTheme("dark");
        s.getAiConfig().setApiKey("sk-export-key");
        store.save(s);

        Path exportFile = tempDir.resolve("backup_settings.json");
        store.exportSettings(exportFile, false);
        assertTrue(Files.exists(exportFile));

        // Mutate current
        s.setBrowser("CHROMIUM");
        s.getAiConfig().setApiKey("sk-new-key");
        store.save(s);
        assertEquals("CHROMIUM", store.load().getBrowser());

        // Import backup
        SettingsStore.Settings imported = store.importSettings(exportFile);
        assertNotNull(imported);
        assertEquals("WEBKIT", imported.getBrowser());
        assertEquals("sk-export-key", imported.getAiConfig().getApiKey());

        // Verify loaded matches imported
        SettingsStore.Settings reloaded = store.load();
        assertEquals("WEBKIT", reloaded.getBrowser());
        assertEquals("sk-export-key", reloaded.getAiConfig().getApiKey());
    }
}
