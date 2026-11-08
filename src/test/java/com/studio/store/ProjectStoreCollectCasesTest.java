package com.studio.store;

import com.studio.model.Assertion;
import com.studio.model.CaseResult;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.util.StudioPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreCollectCasesTest {

    @TempDir
    Path tempDir;

    private ProjectStore store;
    private Path suiteDir;

    @BeforeEach
    void setUp() throws Exception {
        store = new ProjectStore();
        suiteDir = tempDir.resolve("LoginFlow");
        Files.createDirectories(suiteDir);

        TestSuite suite = new TestSuite("LoginFlow", "test");
        suite.setStatus("ACTIVE");

        TestCase readyWithAssert = new TestCase("tc-1", "ReadyOk", "", "ReadyOk.java", "Ready");
        Assertion active = new Assertion();
        active.setType("TITLE_CONTAINS");
        active.setValue("x");
        active.setStatus("ACTIVE");
        readyWithAssert.getAssertions().add(active);

        TestCase readyNoAssert = new TestCase("tc-2", "ReadyEmpty", "", "ReadyEmpty.java", "Ready");

        TestCase draft = new TestCase("tc-3", "DraftCase", "", "DraftCase.java", "Draft");
        Assertion draftAssert = new Assertion();
        draftAssert.setType("URL_CONTAINS");
        draftAssert.setValue("y");
        draftAssert.setStatus("ACTIVE");
        draft.getAssertions().add(draftAssert);

        suite.getCases().add(readyWithAssert);
        suite.getCases().add(readyNoAssert);
        suite.getCases().add(draft);
        store.saveSuite(suite, suiteDir);

        Files.writeString(suiteDir.resolve("ReadyOk.java"), "class ReadyOk {}");
        Files.writeString(suiteDir.resolve("ReadyEmpty.java"), "class ReadyEmpty {}");
        Files.writeString(suiteDir.resolve("DraftCase.java"), "class DraftCase {}");
    }

    @Test
    void collectSuiteScope_skipReasonsPerCase() throws Exception {
        List<CaseResult> results = store.collectCasesForScope("SUITE", suiteDir);
        assertEquals(3, results.size());

        CaseResult readyOk = results.stream().filter(r -> "ReadyOk".equals(r.getTestCaseName())).findFirst().orElseThrow();
        assertEquals("PENDING", readyOk.getStatus());
        assertNull(readyOk.getSkipReason());

        CaseResult readyEmpty = results.stream().filter(r -> "ReadyEmpty".equals(r.getTestCaseName())).findFirst().orElseThrow();
        assertEquals("SKIPPED", readyEmpty.getStatus());
        assertEquals("No active asserts on this case", readyEmpty.getSkipReason());

        CaseResult draftCase = results.stream().filter(r -> "DraftCase".equals(r.getTestCaseName())).findFirst().orElseThrow();
        assertEquals("SKIPPED", draftCase.getStatus());
        assertEquals("No script (case Draft)", draftCase.getSkipReason());
    }

    @Test
    void inactiveSuite_skipsAllWhenEnumeratedViaGroupWalk() throws Exception {
        TestSuite suite = store.loadAndMigrateSuite(suiteDir);
        suite.setStatus("INACTIVE");
        store.saveSuite(suite, suiteDir);

        // ALL/GROUP walk uses enumerateSuiteCases which checks suite ACTIVE
        Path groupDir = tempDir;
        List<CaseResult> results = store.collectCasesForScope("GROUP", groupDir);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(r -> "SKIPPED".equals(r.getStatus())));
        assertTrue(results.stream().allMatch(r -> r.getSkipReason() != null
                && r.getSkipReason().contains("INACTIVE")));
    }

    @Test
    void inactiveSuite_skipsAllWhenCollectedViaSuiteScope() throws Exception {
        TestSuite suite = store.loadAndMigrateSuite(suiteDir);
        suite.setStatus("INACTIVE");
        store.saveSuite(suite, suiteDir);

        List<CaseResult> results = store.collectCasesForScope("SUITE", suiteDir);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(r -> "SKIPPED".equals(r.getStatus())),
                "When suite is INACTIVE, all cases must be SKIPPED");
        assertTrue(results.stream().allMatch(r -> r.getSkipReason() != null
                && r.getSkipReason().contains("INACTIVE")));
    }

    @Test
    void inactiveCase_skipsWhenCollectedInSuiteScope() throws Exception {
        TestSuite suite = store.loadAndMigrateSuite(suiteDir);
        TestCase inactiveCase = new TestCase("tc-inact", "InactiveTest", "", "InactiveTest.java", "Inactive");
        Assertion a = new Assertion();
        a.setType("TITLE_CONTAINS");
        a.setValue("x");
        a.setStatus("ACTIVE");
        inactiveCase.getAssertions().add(a);
        suite.getCases().add(inactiveCase);
        store.saveSuite(suite, suiteDir);
        Files.writeString(suiteDir.resolve("InactiveTest.java"), "class InactiveTest {}");

        List<CaseResult> results = store.collectCasesForScope("SUITE", suiteDir);
        CaseResult cr = results.stream().filter(r -> "InactiveTest".equals(r.getTestCaseName())).findFirst().orElseThrow();
        assertEquals("SKIPPED", cr.getStatus());
        assertEquals("Case is inactive", cr.getSkipReason());
    }

    @Test
    void inactiveCase_skipsWhenEnumeratedViaGroupWalk() throws Exception {
        TestSuite suite = store.loadAndMigrateSuite(suiteDir);
        TestCase inactiveCase = new TestCase("tc-inact2", "InactiveGrpTest", "", "InactiveGrpTest.java", "INACTIVE");
        Assertion a = new Assertion();
        a.setType("TITLE_CONTAINS");
        a.setValue("x");
        a.setStatus("ACTIVE");
        inactiveCase.getAssertions().add(a);
        suite.getCases().add(inactiveCase);
        store.saveSuite(suite, suiteDir);
        Files.writeString(suiteDir.resolve("InactiveGrpTest.java"), "class InactiveGrpTest {}");

        List<CaseResult> results = store.collectCasesForScope("GROUP", tempDir);
        CaseResult cr = results.stream().filter(r -> "InactiveGrpTest".equals(r.getTestCaseName())).findFirst().orElseThrow();
        assertEquals("SKIPPED", cr.getStatus());
        assertEquals("Case is inactive", cr.getSkipReason());
    }

    @Test
    void inactiveCase_skipsWhenCollectedSingleCaseScope() throws Exception {
        TestSuite suite = store.loadAndMigrateSuite(suiteDir);
        TestCase inactiveCase = new TestCase("tc-inact3", "InactiveSingle", "", "InactiveSingle.java", "Inactive");
        Assertion a = new Assertion();
        a.setType("TITLE_CONTAINS");
        a.setValue("x");
        a.setStatus("ACTIVE");
        inactiveCase.getAssertions().add(a);
        suite.getCases().add(inactiveCase);
        store.saveSuite(suite, suiteDir);
        Path script = suiteDir.resolve("InactiveSingle.java");
        Files.writeString(script, "class InactiveSingle {}");

        List<CaseResult> results = store.collectCasesForScope("CASE", script);
        assertEquals(1, results.size());
        assertEquals("SKIPPED", results.get(0).getStatus());
        assertEquals("Case is inactive", results.get(0).getSkipReason());
    }

    @Test
    void collectSuiteAndCaseScope_groupPathIsParentNotSuiteName() throws Exception {
        List<CaseResult> suiteResults = store.collectCasesForScope("SUITE", suiteDir);
        assertFalse(suiteResults.isEmpty());
        for (CaseResult cr : suiteResults) {
            assertEquals("LoginFlow", cr.getSuiteName());
            // Regression: previously both were set to the suite folder name → resolve became suite/suite
            assertNotEquals(cr.getSuiteName(), cr.getGroupPath());
        }

        Path script = suiteDir.resolve("ReadyOk.java");
        List<CaseResult> one = store.collectCasesForScope("CASE", script);
        assertEquals(1, one.size());
        assertEquals("LoginFlow", one.get(0).getSuiteName());
        assertNotEquals("LoginFlow", one.get(0).getGroupPath());
    }

    @Test
    void collectGroupScope_groupPathIncludesGroupFolder() throws Exception {
        Path projectRoot = StudioPaths.getProjectDir();
        Path groupDir = projectRoot.resolve("grp-collect-test");
        Path nestedSuite = groupDir.resolve("NestedSuite");
        Files.createDirectories(nestedSuite);

        TestSuite suite = new TestSuite("NestedSuite", "nested");
        suite.setStatus("ACTIVE");
        TestCase ready = new TestCase("tc-g", "ReadyOk", "", "ReadyOk.java", "Ready");
        Assertion active = new Assertion();
        active.setType("TITLE_CONTAINS");
        active.setValue("x");
        active.setStatus("ACTIVE");
        ready.getAssertions().add(active);
        suite.getCases().add(ready);
        store.saveSuite(suite, nestedSuite);
        Files.writeString(nestedSuite.resolve("ReadyOk.java"), "class ReadyOk {}");
        Files.writeString(groupDir.resolve("group.json"), "{\"name\":\"grp-collect-test\",\"type\":\"group\"}");

        try {
            List<CaseResult> results = store.collectCasesForScope("GROUP", groupDir);
            assertFalse(results.isEmpty());
            for (CaseResult cr : results) {
                assertEquals("grp-collect-test", cr.getGroupPath());
                assertEquals("NestedSuite", cr.getSuiteName());
                assertNotNull(cr.getSuiteDir());
                assertTrue(Files.isDirectory(Path.of(cr.getSuiteDir())));
            }

            List<CaseResult> suiteResults = store.collectCasesForScope("SUITE", nestedSuite);
            assertFalse(suiteResults.isEmpty());
            assertEquals("grp-collect-test", suiteResults.get(0).getGroupPath());
        } finally {
            deleteRecursive(groupDir);
        }
    }

    @Test
    void migratesLegacySuiteAssertionsOntoFirstReadyCase() throws Exception {
        Path legacyDir = tempDir.resolve("Legacy");
        Files.createDirectories(legacyDir);
        String json = """
                {
                  "name": "Legacy",
                  "status": "ACTIVE",
                  "description": "",
                  "cases": [
                    { "id": "tc-a", "name": "A", "scriptFile": "A.java", "status": "Ready", "assertions": [] }
                  ],
                  "assertions": [
                    { "type": "TEXT_VISIBLE", "value": "Hi", "status": "ACTIVE", "exact": false, "timeoutMs": 3000 }
                  ]
                }
                """;
        Files.writeString(legacyDir.resolve("suite.json"), json);
        Files.writeString(legacyDir.resolve("A.java"), "class A {}");

        TestSuite migrated = store.loadAndMigrateSuite(legacyDir);
        assertTrue(migrated.getAssertions().isEmpty());
        assertEquals(1, migrated.getCases().get(0).getAssertions().size());
        assertEquals("TEXT_VISIBLE", migrated.getCases().get(0).getAssertions().get(0).getType());
    }

    @Test
    void createCaseWithSpacesAndKeywordsGeneratesCompilableClassStub() throws Exception {
        Path newSuiteDir = tempDir.resolve("StubSuite");
        Files.createDirectories(newSuiteDir);
        TestSuite suite = new TestSuite("StubSuite", "test");
        suite.setStatus("ACTIVE");
        Files.writeString(newSuiteDir.resolve("suite.json"), "{\"name\":\"StubSuite\",\"cases\":[]}");

        TestCase tc1 = store.createCase(newSuiteDir, "case 2", "test case with space");
        String content1 = Files.readString(newSuiteDir.resolve("case 2.java"));
        assertTrue(content1.contains("public class case_2 {"), content1);
        assertFalse(content1.contains("public class case 2"), content1);

        TestCase tc2 = store.createCase(newSuiteDir, "case", "test case with keyword");
        String content2 = Files.readString(newSuiteDir.resolve("case.java"));
        assertTrue(content2.contains("public class C_case {"), content2);
        assertFalse(content2.contains("public class case {"), content2);
    }

    private static void deleteRecursive(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
            });
        }
    }
}
