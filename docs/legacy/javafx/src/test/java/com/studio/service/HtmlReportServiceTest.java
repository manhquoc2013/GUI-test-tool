package com.studio.service;

import com.studio.model.AssertResult;
import com.studio.model.CaseResult;
import com.studio.model.ExportLanguage;
import com.studio.model.HealedStepMeta;
import com.studio.model.RunReport;
import com.studio.model.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlReportServiceTest {

    @TempDir
    Path tempDir;

    private RunReport report;

    @BeforeEach
    void setUp() throws IOException {
        report = new RunReport("run-test-101", "SUITE", "/groups/SuiteA", "SuiteA", "CHROMIUM", true, true, false);
        report.setStartedAt("2026-11-07T12:00:00Z");
        report.setFinishedAt("2026-11-07T12:00:05Z");
        report.setTotal(2);
        report.setPassed(1);
        report.setFailed(1);
        report.setSkipped(0);

        // Case 1: Passed with screenshot
        Path shotFile = tempDir.resolve("TC01_PASSED.png");
        Files.write(shotFile, new byte[]{1, 2, 3});

        CaseResult c1 = new CaseResult(1, "GroupA", "SuiteA", "tc-1", "TC01_Login_Success",
                "TC01.java", "PASSED", null, 1200, null, "All green", 0, shotFile.toString());
        AssertResult a1 = new AssertResult();
        a1.setIndex(1);
        a1.setType("PAGE_TITLE_CONTAINS");
        a1.setStatus("PASSED");
        c1.setAssertResults(List.of(a1));

        // Case 2: Failed
        CaseResult c2 = new CaseResult(2, "GroupA", "SuiteA", "tc-2", "TC02_Invalid_Password",
                "TC02.java", "FAILED", null, 800, "Element not visible: .error-box", "Stack trace...", 1, null);
        AssertResult a2 = new AssertResult();
        a2.setIndex(1);
        a2.setType("ELEMENT_VISIBLE");
        a2.setStatus("FAILED");
        a2.setMessage("Timeout after 30000ms");
        c2.setAssertResults(List.of(a2));

        report.setCases(List.of(c1, c2));
    }

    @Test
    void testGenerateHtmlContainsStatsAndEvidence() {
        String html = HtmlReportService.generateHtml(report);
        assertNotNull(html);
        assertTrue(html.contains("run-test-101"));
        assertTrue(html.contains("TC01_Login_Success"));
        assertTrue(html.contains("TC02_Invalid_Password"));
        assertTrue(html.contains("50%")); // Pass rate 1/2 = 50%
        assertTrue(html.contains("PAGE_TITLE_CONTAINS"));
        assertTrue(html.contains("ELEMENT_VISIBLE"));
        assertTrue(html.contains("data:image/png;base64,")); // Embedded screenshot
        assertTrue(html.contains("Element not visible: .error-box"));
        assertTrue(html.contains("data-priority=\"p1\""));
        assertTrue(html.contains("Priority:"));
    }

    @Test
    void testExportToFile() throws IOException {
        Path targetFile = tempDir.resolve("reports/output.html");
        HtmlReportService.export(report, targetFile);

        assertTrue(Files.exists(targetFile));
        String readBack = Files.readString(targetFile);
        assertTrue(readBack.contains("<!DOCTYPE html>"));
        assertTrue(readBack.contains("TC01_Login_Success"));
    }

    @Test
    void testGenerateHtmlRendersSelfHealingSection() {
        HealedStepMeta step = new HealedStepMeta(
                "tc-1", "CLICK_LOGIN", "#btn-login-old", "button:has-text('Login')", "HEURISTIC", 0.95, "2026-11-07 12:00:01"
        );
        report.getCases().get(0).setHealedSteps(List.of(step));

        String html = HtmlReportService.generateHtml(report);
        assertTrue(html.contains("Auto-Healed (1)"), "Header badge for self healing");
        assertTrue(html.contains("Tự động sửa lỗi (Self-Healing)"), "Self healing section title");
        assertTrue(html.contains("#btn-login-old"), "Old selector rendered");
        assertTrue(html.contains("button:has-text"), "New selector rendered");
        assertTrue(html.contains("HEURISTIC"), "Healing method rendered");
        assertTrue(html.contains("95%"), "Confidence rendered");
    }

    /**
     * The exported page is what gets shared with people who will never open the Playwright script,
     * so the steps have to be on it — in words, with the failure marked.
     */
    @Test
    void testGenerateHtmlRendersTheExecutedSteps() {
        report.getCases().get(0).setStepResults(List.of(
                new StepResult(1, "page.navigate(\"https://example.com\");", StepResult.PASSED, 320, null),
                new StepResult(2, "page.locator(\"#submit\").click();", StepResult.FAILED, 30000,
                        "TimeoutError: waiting for locator(\"#submit\")")));

        String html = HtmlReportService.generateHtml(report);

        assertTrue(html.contains("Các bước thực thi (2)"), "Steps section title");
        assertTrue(html.contains("mở https://example.com"),
                "The step is phrased, not dumped as Playwright code");
        assertTrue(html.contains("nhấp vào phần tử «#submit»"));
        assertTrue(html.contains("320 ms"));
        assertTrue(html.contains("TimeoutError: waiting for locator"));
        assertTrue(html.contains("<table class=\"steps-table\">"), "Steps table needs its own hook");
        // One verdict, one label: the exported badge must read exactly like the workspace card and
        // the report drawer, not print the raw enum code that the reader would have to translate.
        assertTrue(html.contains(">Đạt</span>"), "A passed step is labelled like everywhere else");
        assertTrue(html.contains(">Thất bại</span>"), "A failed step is labelled like everywhere else");
        assertFalse(html.contains(">PASSED</span>"), "The raw status code must not reach the reader");
    }

    /**
     * A step the run never reached has no duration. Printing "0 ms" would read as "it ran instantly",
     * so the cell stays empty and the label says it did not run.
     */
    @Test
    void aStepThatNeverRanShowsNoDurationAndSaysSo() {
        report.getCases().get(0).setStepResults(List.of(
                new StepResult(2, "page.locator(\"#late\").click();", StepResult.NOT_RUN, 0,
                        "Không chạy vì bước trước bị lỗi")));

        String html = HtmlReportService.generateHtml(report);

        assertTrue(html.contains(">Không chạy</span>"), html);
        assertTrue(html.contains("<td>-</td>"), "A step that never ran must not claim a duration");
        assertFalse(html.contains(">0 ms</td>"));
    }

    @Test
    void aStepWithoutResultsAddsNoEmptyStepsSection() {
        String html = HtmlReportService.generateHtml(report);
        assertFalse(html.contains("<table class=\"steps-table\">"),
                "A report without step results must not show the section");
    }

    @Test
    void stepStatementsAreEscapedSoARecordedSelectorCannotInjectHtml() {
        report.getCases().get(0).setStepResults(List.of(
                new StepResult(1, "helper.call(\"<script>alert(1)</script>\");", StepResult.PASSED, 5, null)));

        String html = HtmlReportService.generateHtml(report);

        assertTrue(html.contains("&lt;script&gt;"), "The statement must be escaped");
        assertFalse(html.contains("<script>alert(1)</script>"), "Raw script tag leaked into the report");
    }

    @Test
    void testGenerateHtmlEnglish() {
        HealedStepMeta step = new HealedStepMeta(
                "tc-1", "CLICK_LOGIN", "#btn-login-old", "button:has-text('Login')", "HEURISTIC", 0.95, "2026-11-07 12:00:01"
        );
        report.getCases().get(0).setHealedSteps(List.of(step));
        report.getCases().get(0).setStepResults(List.of(
                new StepResult(1, "page.reload();", StepResult.PASSED, 7, null),
                new StepResult(2, "page.locator(\"#go\").click();", StepResult.FAILED, 900, "TimeoutError")));

        String html = HtmlReportService.generateHtml(report, ExportLanguage.ENGLISH);
        assertNotNull(html);
        assertTrue(html.contains("<html lang=\"en\">"));
        assertTrue(html.contains("Test Execution Report — "));
        assertTrue(html.contains("Total Cases"));
        assertTrue(html.contains("Passed"));
        assertTrue(html.contains("Failed"));
        assertTrue(html.contains("Pass Rate"));
        assertTrue(html.contains("Filter:"));
        assertTrue(html.contains("All (2)"));
        assertTrue(html.contains("Self-Healing Operations (1 steps)"));
        // The verdict words follow the report's language: an English export must not print the
        // Vietnamese labels the app itself uses.
        assertTrue(html.contains(">Passed</span>"), "Case and step badges are in English");
        assertTrue(html.contains(">Failed</span>"));
        assertFalse(html.contains(">Đạt</span>"), "Vietnamese leaked into the English export");
        assertFalse(html.contains(">Thất bại</span>"));
        assertTrue(html.contains("Original Selector"));
        assertTrue(html.contains("Repaired Selector"));
        assertTrue(html.contains("Report automatically generated by Test Studio"));
    }
}

