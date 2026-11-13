package com.studio.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class StudioPathsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        System.clearProperty("studio.data.dir");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("studio.data.dir");
        StudioPaths.resetForTesting();
    }

    @Test
    void testPortableModeUsesAppHomeForData() {
        Path mockPortable = tempDir.resolve("portable-app");
        StudioPaths.setPathsForTesting(mockPortable, null);

        assertEquals(mockPortable, StudioPaths.getAppHome());
        assertEquals(mockPortable, StudioPaths.getDataHome());
        assertEquals(mockPortable.resolve("data"), StudioPaths.getDataDir());
        assertEquals(mockPortable.resolve("data").resolve("project"), StudioPaths.getProjectDir());
        assertEquals(mockPortable.resolve("work"), StudioPaths.getWorkDir());
    }

    @Test
    void testMacApplicationsModeRedirectsDataHome() {
        Path mockMacApp = Paths.get("/Applications/TestStudio.app/Contents");
        assertTrue(StudioPaths.isSystemOrReadOnlyLocation(mockMacApp));

        StudioPaths.setPathsForTesting(mockMacApp, null);

        assertEquals(mockMacApp, StudioPaths.getAppHome());
        assertNotEquals(mockMacApp, StudioPaths.getDataHome());
        String dataHomeStr = StudioPaths.getDataHome().toString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(dataHomeStr.contains("studio"),
                "Data home should contain app identifier regardless of OS (mac, win, or linux xdg): " + dataHomeStr);
        assertEquals(StudioPaths.getDataHome().resolve("data"), StudioPaths.getDataDir());
        assertEquals(StudioPaths.getDataHome().resolve("work"), StudioPaths.getWorkDir());
        // Static resources still point to APP_HOME
        assertEquals(mockMacApp.resolve("browsers"), StudioPaths.getBrowsersDir());
    }

    @Test
    void testWindowsProgramFilesModeRedirectsDataHome() {
        Path mockWinApp = Paths.get("C:\\Program Files\\TestStudio");
        assertTrue(StudioPaths.isSystemOrReadOnlyLocation(mockWinApp));

        StudioPaths.setPathsForTesting(mockWinApp, null);

        assertEquals(mockWinApp, StudioPaths.getAppHome());
        assertNotEquals(mockWinApp, StudioPaths.getDataHome());
        String dataHomeStr = StudioPaths.getDataHome().toString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(dataHomeStr.contains("studio"),
                "Data home should contain app identifier: " + dataHomeStr);
    }

    @Test
    void testUnixSystemDirectoriesModeRedirectsDataHome() {
        Path mockUnixApp = Paths.get("/opt/test-studio");
        assertTrue(StudioPaths.isSystemOrReadOnlyLocation(mockUnixApp));

        StudioPaths.setPathsForTesting(mockUnixApp, null);

        assertEquals(mockUnixApp, StudioPaths.getAppHome());
        assertNotEquals(mockUnixApp, StudioPaths.getDataHome());
        String dataHomeStr = StudioPaths.getDataHome().toString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(dataHomeStr.contains("studio"));
    }

    @Test
    void testExplicitSystemPropertyOverride() {
        Path customData = tempDir.resolve("custom-data-dir");
        System.setProperty("studio.data.dir", customData.toString());

        Path mockApp = tempDir.resolve("app");
        StudioPaths.setPathsForTesting(mockApp, null);

        assertEquals(customData, StudioPaths.getDataHome());
        assertEquals(customData.resolve("data"), StudioPaths.getDataDir());
    }

    @Test
    void testEnsureDirectoriesCreatesAllPaths() throws IOException {
        Path testRoot = tempDir.resolve("sandbox");
        StudioPaths.setPathsForTesting(testRoot, testRoot);

        StudioPaths.ensureDirectories();

        assertTrue(Files.isDirectory(StudioPaths.getDataDir()));
        assertTrue(Files.isDirectory(StudioPaths.getProjectDir()));
        assertTrue(Files.isDirectory(StudioPaths.getReportsDir()));
        assertTrue(Files.isDirectory(StudioPaths.getConfigDir()));
        assertTrue(Files.isDirectory(StudioPaths.getExportsDir()));
        assertTrue(Files.isDirectory(StudioPaths.getLogsDir()));
        assertTrue(Files.isDirectory(StudioPaths.getWorkDir()));
        assertTrue(Files.isDirectory(StudioPaths.getCompileDir()));
        assertTrue(Files.isDirectory(StudioPaths.getHarnessDir()));
    }
}

