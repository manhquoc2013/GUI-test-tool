package com.studio.store;

import com.studio.model.TestSuite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreAtomicWriteTest {

    @TempDir
    Path tempDir;

    @Test
    void testWriteJsonAtomicCreatesFileAndParentDirectories() throws IOException {
        Path deepTarget = tempDir.resolve("nested").resolve("dir").resolve("data.json");
        Map<String, String> payload = Map.of("key", "value", "status", "active");

        ProjectStore.writeJsonAtomic(deepTarget, payload);

        assertTrue(Files.exists(deepTarget), "Target file must exist after atomic write");
        String content = Files.readString(deepTarget);
        assertTrue(content.contains("\"key\" : \"value\""));
        assertTrue(content.contains("\"status\" : \"active\""));
    }

    @Test
    void testSaveSuiteUsesAtomicWrite() throws IOException {
        ProjectStore store = new ProjectStore();
        Path suiteDir = tempDir.resolve("TestGroup").resolve("AtomicSuite");

        TestSuite suite = new TestSuite("AtomicSuite", "Description for atomic test");
        store.saveSuite(suite, suiteDir);

        Path suiteJson = suiteDir.resolve("suite.json");
        assertTrue(Files.exists(suiteJson), "suite.json must be written atomically");

        TestSuite reloaded = store.loadAndMigrateSuite(suiteDir);
        assertEquals("AtomicSuite", reloaded.getName());
        assertEquals("Description for atomic test", reloaded.getDescription());
    }
}

