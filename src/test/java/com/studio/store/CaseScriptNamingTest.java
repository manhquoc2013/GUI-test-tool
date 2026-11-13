package com.studio.store;

import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renaming and duplicating move and copy script files. Deriving the target name from the new title alone
 * lands on a file another case already owns: a rename would `move` over it, a duplicate would share it —
 * and the first save of the copy would rewrite the original's code.
 */
class CaseScriptNamingTest {

    @TempDir
    Path tempDir;

    private final ProjectStore store = new ProjectStore();

    @Test
    void renamingACaseOntoAnotherCasesTitleKeepsBothScripts() throws IOException {
        Path suiteDir = newSuite();
        TestCase alpha = store.createCase(suiteDir, "Alpha", "");
        TestCase beta = store.createCase(suiteDir, "Beta", "");
        store.saveCaseScript(suiteDir, alpha.getId(), code("Alpha"));
        store.saveCaseScript(suiteDir, beta.getId(), code("Beta"));

        store.renameCase(suiteDir, beta.getId(), "Alpha");

        TestSuite suite = store.loadAndMigrateSuite(suiteDir);
        TestCase renamed = caseById(suite, beta.getId());
        assertNotEquals(caseById(suite, alpha.getId()).getScriptFile(), renamed.getScriptFile(),
                "The rename must not land on the file the other case owns");
        assertEquals(code("Alpha"), store.readCaseScript(suiteDir, alpha.getId()),
                "The other case's code survives the rename");
        assertEquals(code("Beta"), store.readCaseScript(suiteDir, beta.getId()),
                "The renamed case keeps its own code, now under its new file");
    }

    @Test
    void duplicatingACaseGivesTheCopyItsOwnFile() throws IOException {
        Path suiteDir = newSuite();
        TestCase alpha = store.createCase(suiteDir, "Alpha", "");
        store.saveCaseScript(suiteDir, alpha.getId(), code("Alpha"));

        TestCase copy = store.copyCase(suiteDir, alpha.getId(), "Alpha");

        assertNotEquals(alpha.getScriptFile(), copy.getScriptFile(),
                "A copy that shares the original's file rewrites the original on its first save");
        assertTrue(Files.isRegularFile(suiteDir.resolve(copy.getScriptFile())), "The copy has its own file");
        assertEquals(code("Alpha"), store.readCaseScript(suiteDir, copy.getId()),
                "The copy starts from the original's code");

        store.saveCaseScript(suiteDir, copy.getId(), code("Copy"));
        assertEquals(code("Alpha"), store.readCaseScript(suiteDir, alpha.getId()),
                "Editing the copy leaves the original untouched");
    }

    @Test
    void aCaseKeepsItsCodeWhenItIsRenamed() throws IOException {
        Path suiteDir = newSuite();
        TestCase alpha = store.createCase(suiteDir, "Alpha", "");
        store.saveCaseScript(suiteDir, alpha.getId(), code("Alpha"));

        store.renameCase(suiteDir, alpha.getId(), "Alpha renamed");

        TestCase renamed = caseById(store.loadAndMigrateSuite(suiteDir), alpha.getId());
        assertEquals("Alpha renamed.java", renamed.getScriptFile(),
                "The script file follows the case title: " + renamed.getScriptFile());
        assertTrue(Files.isRegularFile(suiteDir.resolve("Alpha renamed.java")), "The file was moved");
        assertEquals(code("Alpha"), store.readCaseScript(suiteDir, alpha.getId()),
                "Renaming a case must not touch its code");
    }

    private Path newSuite() throws IOException {
        Path suiteDir = tempDir.resolve("NamingSuite");
        Files.createDirectories(suiteDir);
        TestSuite suite = new TestSuite("NamingSuite", "naming");
        store.saveSuite(suite, suiteDir);
        return suiteDir;
    }

    private static TestCase caseById(TestSuite suite, String caseId) {
        List<TestCase> cases = suite.getCases();
        return cases.stream()
                .filter(c -> caseId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No case " + caseId));
    }

    private static String code(String className) {
        return "package com.test;\npublic class " + className + " {}\n";
    }
}
