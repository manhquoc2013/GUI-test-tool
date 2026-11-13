package com.studio.service;

import com.studio.util.StudioPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiLoggingServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        StudioPaths.ensureDirectories();
        AiLoggingService.clearForTesting();
    }

    @AfterEach
    void tearDown() {
        AiLoggingService.clearForTesting();
    }

    @Test
    void testLoggingInMemoryAndDisk() throws IOException {
        AiLoggingService.info("TEST-CAT", "Testing info log");
        AiLoggingService.warn("TEST-CAT", "Testing warn log");
        AiLoggingService.error("TEST-CAT", "Testing error log", new IllegalArgumentException("Bad input"));

        List<String> logs = AiLoggingService.getRecentLogs();
        assertEquals(3, logs.size());
        assertTrue(logs.get(0).contains("[INFO ] [TEST-CAT] Testing info log"));
        assertTrue(logs.get(1).contains("[WARN ] [TEST-CAT] Testing warn log"));
        assertTrue(logs.get(2).contains("[ERROR] [TEST-CAT] Testing error log | IllegalArgumentException: Bad input"));

        String stringLogs = AiLoggingService.getRecentLogsAsString(2);
        assertTrue(stringLogs.contains("Testing warn log"));
        assertTrue(stringLogs.contains("Testing error log"));

        Path logFile = StudioPaths.getLogsDir().resolve("ai.log");
        assertTrue(Files.exists(logFile), "ai.log must be created on disk");
        String content = Files.readString(logFile);
        assertTrue(content.contains("Testing info log"));
        assertTrue(content.contains("Testing warn log"));
    }
}
