package com.studio.service;

import com.studio.model.AssertResult;
import com.studio.model.CaseResult;
import com.studio.model.RunReport;
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
}
