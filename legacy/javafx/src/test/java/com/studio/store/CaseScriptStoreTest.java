package com.studio.store;

import com.studio.util.StringEscapeUtil;
import com.studio.ui.StepScriptBidirectionalSync;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A case can exist before its script does — the AI writes scenario cases from a specification and
 * generates code later — so "no script yet" has to be a value callers can see, not an exception they
 * have to decode. The read used to derive a different file name than save whenever the case name held
 * path characters, which turned a saved script into a "not found" on the next open.
 */
class CaseScriptStoreTest {

    private static final String SCRIPT_CODE = "package com.test;\npublic class Case {}\n";

    @TempDir
    Path tempDir;

    private final ProjectStore store = new ProjectStore();

    @Test
    void aCaseWithoutItsScriptReportsNoScriptInsteadOfAFailure() throws IOException {
        Path suiteDir = suiteWithCase("tc-1", "TC01_Chua_Co_Script", "TC01_Chua_Co_Script.java");

        Optional<String> script = store.findCaseScript(suiteDir, "tc-1");
        assertTrue(script.isEmpty(), "A case whose script file is absent has no script, not an error");
        assertFalse(Files.exists(suiteDir.resolve("TC01_Chua_Co_Script.java")),
                "The fixture must not create the missing file");

        IOException error = assertThrows(IOException.class, () -> store.readCaseScript(suiteDir, "tc-1"));
        assertTrue(error.getMessage().contains("Script file not found"),
                "A caller that requires the code still fails loudly, got: " + error.getMessage());
    }

    @Test
    void readingFindsTheSameFileNameThatSavingWrites() throws IOException {
        // A case created outside createCase (import, older data) may carry no script file name.
        Path suiteDir = suiteWithCase("tc-2", "TC 01: Login/Test", "");
        String expectedFile = StringEscapeUtil.safeFileName("TC 01: Login/Test", "case") + ".java";
        assertTrue(expectedFile.contains("_"), "The fixture name must need sanitising");

        store.saveCaseScript(suiteDir, "tc-2", SCRIPT_CODE);

        assertTrue(Files.exists(suiteDir.resolve(expectedFile)),
                "Saving writes the sanitised file name");
        assertEquals(SCRIPT_CODE, store.findCaseScript(suiteDir, "tc-2").orElseThrow(),
                "Reading must resolve the name saving derived instead of the raw case name");
        assertEquals(SCRIPT_CODE, store.readCaseScript(suiteDir, "tc-2"));
    }

    @Test
    void anUnknownCaseIsStillAnError() throws IOException {
        Path suiteDir = suiteWithCase("tc-3", "TC03_Co_Script", "TC03_Co_Script.java");

        IOException findError = assertThrows(IOException.class,
                () -> store.findCaseScript(suiteDir, "tc-missing"));
        assertTrue(findError.getMessage().contains("Case not found"), findError.getMessage());
        assertThrows(IOException.class, () -> store.readCaseScript(suiteDir, "tc-missing"));
    }

    /**
     * The store hands callers one invariant — a present script is never blank — so the workspace and the
     * runner do not each have to re-test for empty content.
     */
    @Test
    void aBlankScriptFileCountsAsNoScript() throws IOException {
        Path suiteDir = suiteWithCase("tc-4", "TC04_Script_Rong", "TC04_Script_Rong.java");
        Files.writeString(suiteDir.resolve("TC04_Script_Rong.java"), "  \n\t\n");

        assertTrue(store.findCaseScript(suiteDir, "tc-4").isEmpty(),
                "A whitespace-only file is not a script");
    }

    @Test
    void theSkeletonIsTheEnvelopeTheBuilderFillsIn() {
        String skeleton = ProjectStore.scriptSkeleton("TC 01: Login/Test");

        assertTrue(skeleton.contains("public class TC_01_Login_Test"),
                "The class name is a valid Java identifier derived from the case name: " + skeleton);
        assertTrue(skeleton.contains("public static void main"),
                "The runner renames an existing class, it never synthesises main(): " + skeleton);
        assertTrue(skeleton.contains("Page page = com.studio.runtime.StudioRuntime.page();"),
                "Steps are emitted against StudioRuntime.page(), so the skeleton must declare it: "
                        + skeleton);
        assertTrue(StepScriptBidirectionalSync.parseDocument(skeleton).getSteps().isEmpty(),
                "A skeleton comment would show up as an authored NOTE card");
    }

    /**
     * Two case names can sanitise to the same file name ("A/B" and "A_B"), and the AI does create cases
     * with a repeated name. Deriving one file for both means the second save overwrites the first case's
     * code — silently, since both cases stay listed with their own names.
     */
    @Test
    void twoCaseNamesThatSanitiseTheSameDoNotShareOneScriptFile() throws IOException {
        Path suiteDir = tempDir.resolve("CollisionSuite");
        Files.createDirectories(suiteDir);
        Files.writeString(suiteDir.resolve("suite.json"), "{\"name\":\"CollisionSuite\",\"cases\":[]}");

        var first = store.createCase(suiteDir, "Login / Tạo hồ sơ", "");
        var second = store.createCase(suiteDir, "Login _ Tạo hồ sơ", "");

        assertNotEquals(first.getScriptFile(), second.getScriptFile(),
                "Two cases must never be backed by one file: " + first.getScriptFile());
        store.saveCaseScript(suiteDir, first.getId(), "package com.test;\nclass First {}\n");
        store.saveCaseScript(suiteDir, second.getId(), "package com.test;\nclass Second {}\n");

        assertEquals("package com.test;\nclass First {}\n", store.readCaseScript(suiteDir, first.getId()),
                "Saving the second case must not overwrite the first case's code");
        assertEquals("package com.test;\nclass Second {}\n", store.readCaseScript(suiteDir, second.getId()));
    }

    /**
     * The read side has to know about the collision too: a case with no file of its own must not open the
     * neighbouring case's script just because their names sanitise the same.
     */
    @Test
    void aCaseWithNoFileOfItsOwnDoesNotReadItsNeighboursScript() throws IOException {
        Path suiteDir = tempDir.resolve("NeighbourSuite");
        Files.createDirectories(suiteDir);
        Files.writeString(suiteDir.resolve("suite.json"), """
                {
                  "name": "NeighbourSuite",
                  "cases": [
                    { "id": "tc-recorded", "name": "Login / Test", "scriptFile": "Login _ Test.java",
                      "status": "Ready", "assertions": [] },
                    { "id": "tc-blank", "name": "Login _ Test", "scriptFile": "",
                      "status": "Ready", "assertions": [] }
                  ]
                }
                """);
        Files.writeString(suiteDir.resolve("Login _ Test.java"), "package com.test;\nclass Recorded {}\n");

        assertTrue(store.findCaseScript(suiteDir, "tc-blank").isEmpty(),
                "A case with no script of its own must not open the neighbouring case's code");
        assertEquals("package com.test;\nclass Recorded {}\n",
                store.readCaseScript(suiteDir, "tc-recorded"));
    }

    private Path suiteWithCase(String caseId, String caseName, String scriptFile) throws IOException {
        Path suiteDir = tempDir.resolve("Suite_" + caseId);
        Files.createDirectories(suiteDir);
        Files.writeString(suiteDir.resolve("suite.json"), """
                {
                  "name": "ScriptSuite",
                  "cases": [
                    {
                      "id": "%s",
                      "name": "%s",
                      "scriptFile": "%s",
                      "status": "Ready",
                      "assertions": []
                    }
                  ]
                }
                """.formatted(caseId, caseName, scriptFile));
        return suiteDir;
    }
}
