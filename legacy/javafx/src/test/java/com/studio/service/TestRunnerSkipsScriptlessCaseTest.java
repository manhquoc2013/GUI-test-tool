package com.studio.service;

import com.studio.db.DatabaseManager;
import com.studio.model.Assertion;
import com.studio.model.CaseResult;
import com.studio.model.RunReport;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.util.StudioPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suite run over cases whose code does not exist yet must finish without a browser and without a
 * failure. This is the exact state the AI leaves behind: scenario cases written from a specification,
 * some with no script file and some with a file of notes. Both used to surface as a red toast.
 *
 * <p>Runs the real {@link TestRunnerService} — no Playwright session is created because every case is
 * skipped before the harness step — so the skip counters are the ones a report would carry.</p>
 */
class TestRunnerSkipsScriptlessCaseTest {

    @TempDir
    Path tempHome;

    private ProjectStore projectStore;
    private Path suiteDir;

    @BeforeEach
    void setUp() throws IOException {
        StudioPaths.setPathsForTesting(tempHome, tempHome);
        DatabaseManager.setInstance(null);
        projectStore = new ProjectStore();

        suiteDir = tempHome.resolve("project").resolve("KiemThu_TaoHoSoNguoiYeuThe");
        Files.createDirectories(suiteDir);

        TestSuite suite = new TestSuite("KiemThu_TaoHoSoNguoiYeuThe", "Scenario cases without code");
        suite.setStatus("ACTIVE");
        suite.getCases().add(readyCaseWithAssert("tc-nofile", "TC01_Chua_Co_Script", ""));
        suite.getCases().add(readyCaseWithAssert("tc-notes", "TC02_Chi_Co_Ghi_Chu",
                "TC02_Chi_Co_Ghi_Chu.java"));
        projectStore.saveSuite(suite, suiteDir);

        Files.writeString(suiteDir.resolve("TC02_Chi_Co_Ghi_Chu.java"), """
                // [THÔNG BÁO] Không tạo test script tự động vì yêu cầu không có URL/link trang web.
                // Bộ test case đã được thiết kế đầy đủ các bước kiểm thử tại các tab bên cạnh.
                """);
    }

    @AfterEach
    void tearDown() {
        StudioPaths.resetForTesting();
        DatabaseManager.setInstance(null);
    }

    @Test
    void aSuiteWithoutAnyScriptIsSkippedWithoutLaunchingABrowser() throws IOException {
        List<String> terminal = new ArrayList<>();
        TestRunnerService runner = new TestRunnerService(projectStore, new ReportStore());

        RunReport report = runner.run("SUITE", suiteDir, true, "CHROMIUM", false, false, terminal::add);

        assertEquals(2, report.getTotal(), "Both scenario cases are enumerated");
        assertEquals(0, report.getPassed(), "Nothing executed, so nothing passed");
        assertEquals(0, report.getFailed(), "Missing code is not a failure");
        assertEquals(2, report.getSkipped(), "Both cases are skipped");

        CaseResult missing = caseNamed(report, "TC01_Chua_Co_Script");
        assertEquals("SKIPPED", missing.getStatus());
        assertEquals(TestRunnerService.SKIP_NO_SCRIPT, missing.getSkipReason());
        assertNull(missing.getFailureReason(), "A skip carries no failure reason");

        CaseResult notes = caseNamed(report, "TC02_Chi_Co_Ghi_Chu");
        assertEquals("SKIPPED", notes.getStatus(),
                "A file of notes is not runnable: it must not be reported as a compile failure");
        assertEquals(TestRunnerService.SKIP_NOT_A_SCRIPT, notes.getSkipReason());
        assertNull(notes.getFailureReason(), "A note-only script carries no failure reason");

        assertTrue(terminal.stream().anyMatch(line -> line.startsWith("SKIP: TC01_Chua_Co_Script")),
                "The terminal tells the reader why nothing ran: " + terminal);
    }

    private static CaseResult caseNamed(RunReport report, String name) {
        return report.getCases().stream()
                .filter(r -> name.equals(r.getTestCaseName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for " + name + " in " + report.getCases()));
    }

    private static TestCase readyCaseWithAssert(String id, String name, String scriptFile) {
        TestCase tc = new TestCase(id, name, "", scriptFile, "Ready");
        Assertion assertion = new Assertion();
        assertion.setType("TITLE_CONTAINS");
        assertion.setValue("Quản lý");
        assertion.setStatus("ACTIVE");
        tc.getAssertions().add(assertion);
        return tc;
    }
}
