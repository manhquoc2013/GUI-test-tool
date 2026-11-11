package com.studio.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreAiTest {

    @TempDir
    Path tempDir;

    @Test
    void testSerializationWithAiConfig() throws IOException {        SettingsStore.Settings settings = new SettingsStore.Settings();
        settings.getAiConfig().setApiKey("sk-custom-token");
        settings.getAiConfig().setModel("o3-mini");
        settings.getAiConfig().setBaseUrl("https://custom.openai.proxy/v1");

        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = tempDir.resolve("settings.json").toFile();
        mapper.writeValue(jsonFile, settings);

        SettingsStore.Settings loaded = mapper.readValue(jsonFile, SettingsStore.Settings.class);
        assertNotNull(loaded.getAiConfig());
        assertEquals("sk-custom-token", loaded.getAiConfig().getApiKey());
        assertEquals("o3-mini", loaded.getAiConfig().getModel());
        assertEquals("https://custom.openai.proxy/v1", loaded.getAiConfig().getBaseUrl());
    }

    /**
     * The storage copy rebuilds the AiConfig field by field; any budget left out of that copy silently
     * reset the user's limits on every save.
     */
    @Test
    void outputBudgetsSurviveSaveAndLoad() throws IOException {
        SettingsStore store = new SettingsStore(tempDir);
        SettingsStore.Settings settings = new SettingsStore.Settings();
        settings.getAiConfig().setApiKey("sk-budget-test-key");
        settings.getAiConfig().setMaxOutputTokens(12000);
        settings.getAiConfig().setOutputTokenCeiling(48000);

        store.save(settings);

        SettingsStore.Settings loaded = store.load();
        assertNotNull(loaded.getAiConfig());
        assertEquals(12000, loaded.getAiConfig().getMaxOutputTokens(),
                "the per-request output budget must survive a save");
        assertEquals(48000, loaded.getAiConfig().getOutputTokenCeiling(),
                "the configured ceiling must survive a save");
    }
}
