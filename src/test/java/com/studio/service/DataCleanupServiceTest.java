package com.studio.service;

import com.studio.db.DatabaseManager;
import com.studio.model.RunReport;
import com.studio.store.AiChatHistoryStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.util.StudioPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataCleanupServiceTest {

    private DatabaseManager dbManager;
    private ReportStore reportStore;
    private DataCleanupService cleanupService;

    @BeforeEach
    public void setup() {
        dbManager = DatabaseManager.createInMemory("cleanup_test_" + System.currentTimeMillis());
        DatabaseManager.setInstance(dbManager);
        reportStore = new ReportStore();
        cleanupService = new DataCleanupService(dbManager, reportStore);
    }

    @Test
    public void testCleanReportsWithRetentionLimit() throws IOException {
        // Setup 4 reports
        for (int i = 1; i <= 4; i++) {
            RunReport r = new RunReport("run-" + i, "suite", "path", "SuiteA", "chromium", true, false, false);
            r.setStartedAt("2026-11-0" + i + "T10:00:00");
            r.setTotal(5);
            r.setPassed(5);
            reportStore.saveReport(r);
        }

        assertEquals(4, reportStore.listReports().size());

        // Keep 2 most recent reports (run-4 and run-3)
        DataCleanupService.CleanupOptions options = new DataCleanupService.CleanupOptions();
        options.setCleanReports(true);
        options.setKeepRecentReports(2);
        options.setCleanAiChat(false);
        options.setCleanWorkDir(false);

        DataCleanupService.CleanupResult result = cleanupService.executeCleanup(options);

        assertEquals(2, result.getDeletedReportsCount());
        assertTrue(result.isSettingsPreserved());

        List<RunReport> remaining = reportStore.listReports();
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().anyMatch(r -> "run-4".equals(r.getRunId())));
        assertTrue(remaining.stream().anyMatch(r -> "run-3".equals(r.getRunId())));
        assertFalse(remaining.stream().anyMatch(r -> "run-1".equals(r.getRunId())));
        assertFalse(remaining.stream().anyMatch(r -> "run-2".equals(r.getRunId())));
    }

    @Test
    public void testCleanAiChatAndWorkDir(@TempDir Path tempDir) throws IOException {
        // 1. Setup mock chat file
        Path projDir = StudioPaths.getProjectDir().resolve("TestGroup").resolve("SuiteTest");
        Files.createDirectories(projDir);
        Path chatFile = projDir.resolve(AiChatHistoryStore.CHAT_HISTORY_FILE);
        Files.writeString(chatFile, "{\"messages\":[]}", StandardCharsets.UTF_8);

        // 2. Setup mock work file
        Path workDir = StudioPaths.getWorkDir().resolve("temp-harness");
        Files.createDirectories(workDir);
        Path tempFile = workDir.resolve("TempClass.java");
        Files.writeString(tempFile, "// temp code", StandardCharsets.UTF_8);

        // 3. Setup settings in temp dir to avoid polluting actual user settings
        SettingsStore settingsStore = new SettingsStore(tempDir);
        SettingsStore.Settings s = new SettingsStore.Settings();
        s.setBrowser("EDGE");
        settingsStore.save(s);

        assertTrue(Files.exists(chatFile));
        assertTrue(Files.exists(tempFile));

        // 4. Run cleanup
        DataCleanupService.CleanupOptions options = new DataCleanupService.CleanupOptions(false, 0, true, true);
        DataCleanupService.CleanupResult result = cleanupService.executeCleanup(options);

        assertTrue(result.getDeletedChatCount() >= 1);
        assertTrue(result.getDeletedWorkFilesCount() >= 1);
        assertFalse(Files.exists(chatFile));
        assertFalse(Files.exists(tempFile));

        // Settings must remain completely preserved!
        assertEquals("EDGE", settingsStore.load().getBrowser());
        assertTrue(result.isSettingsPreserved());
    }

    @Test
    public void testCleanConvertedDocsDirectory() throws IOException {
        Path convertedDocs = StudioPaths.getDataDir().resolve("converted_docs");
        Files.createDirectories(convertedDocs);
        Path mockDoc = convertedDocs.resolve("sample_spec_12345.md");
        Files.writeString(mockDoc, "# Sample Converted Markdown Spec", StandardCharsets.UTF_8);

        assertTrue(Files.exists(mockDoc));

        DataCleanupService.DiskUsageStats statsBefore = cleanupService.calculateDiskUsage();
        assertTrue(statsBefore.workFilesCount >= 1);

        DataCleanupService.CleanupOptions options = new DataCleanupService.CleanupOptions(false, 0, false, true);
        DataCleanupService.CleanupResult result = cleanupService.executeCleanup(options);

        assertTrue(result.getDeletedWorkFilesCount() >= 1);
        assertFalse(Files.exists(mockDoc), "Converted doc file should be deleted upon work cleanup");
    }

    @Test
    public void testCleanupAbortsWhenRunIsActive() {
        RunCancelService.beginRun();
        try {
            DataCleanupService.CleanupOptions options = new DataCleanupService.CleanupOptions(true, 0, true, true);
            assertThrows(IllegalStateException.class, () -> cleanupService.executeCleanup(options));
        } finally {
            RunCancelService.endRun();
        }
    }
}
