package com.studio.db;

import com.studio.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LocalDataIndexServiceTest {

    private DatabaseManager dbManager;
    private LocalDataIndexService indexService;
    private ProjectDbService projectDb;
    private ReportDbService reportDb;
    private AiChatDbService chatDb;

    @BeforeEach
    public void setup() {
        dbManager = DatabaseManager.createInMemory("index_test_" + System.currentTimeMillis());
        indexService = new LocalDataIndexService(dbManager, new com.fasterxml.jackson.databind.ObjectMapper());
        projectDb = new ProjectDbService(dbManager);
        reportDb = new ReportDbService(dbManager);
        chatDb = new AiChatDbService(dbManager);
    }

    @Test
    public void testProjectIndexingAndFastSearch() {
        // 1. Index Group & Suite
        TestGroup group = new TestGroup("Auth", "Authentication flows");
        indexService.indexGroup(group, null);

        TestSuite suite = new TestSuite("LoginSuite", "Login tests");
        suite.setBrowser("chromium");

        // Case 1: LoginSuccess
        TestCase tc1 = new TestCase();
        tc1.setId("tc-1");
        tc1.setName("LoginSuccess");
        tc1.setDescription("Valid login scenario");
        tc1.setStatus("Ready");
        Assertion a1 = new Assertion();
        a1.setType("PAGE_TITLE_CONTAINS");
        a1.setValue("Dashboard");
        tc1.setAssertions(List.of(a1));

        // Case 2: LoginInvalid
        TestCase tc2 = new TestCase();
        tc2.setId("tc-2");
        tc2.setName("LoginInvalid");
        tc2.setDescription("Invalid password scenario");
        tc2.setStatus("Draft");

        suite.setCases(List.of(tc1, tc2));
        indexService.indexSuite(suite, null, "Auth");

        // Verify indexing
        assertEquals(2, projectDb.getTotalCasesCount());
        assertEquals(1, projectDb.getTotalAssertionsCount());

        Map<String, Integer> statusMap = projectDb.getCasesCountByStatus();
        assertEquals(1, statusMap.get("Ready"));
        assertEquals(1, statusMap.get("Draft"));

        // Search by keyword in name
        List<TestCase> search1 = projectDb.searchCases("Success", null);
        assertEquals(1, search1.size());
        assertEquals("LoginSuccess", search1.get(0).getName());
        assertEquals(1, search1.get(0).getAssertions().size());

        // Search by status filter
        List<TestCase> search2 = projectDb.searchCases(null, "Draft");
        assertEquals(1, search2.size());
        assertEquals("LoginInvalid", search2.get(0).getName());

        // Test delete case
        indexService.deleteCase("tc-2");
        assertEquals(1, projectDb.getTotalCasesCount());
    }

    @Test
    public void testReportAnalyticsAggregation() {
        RunReport r1 = new RunReport("run-1", "suite", "path", "AuthSuite", "chromium", true, false, false);
        r1.setStartedAt("2026-11-07T10:00:00");
        r1.setTotal(10);
        r1.setPassed(10);
        r1.setFailed(0);
        r1.setSkipped(0);
        indexService.indexReport(r1);

        RunReport r2 = new RunReport("run-2", "suite", "path", "CartSuite", "chromium", true, false, false);
        r2.setStartedAt("2026-11-07T11:00:00");
        r2.setTotal(10);
        r2.setPassed(5);
        r2.setFailed(5);
        r2.setSkipped(0);
        indexService.indexReport(r2);

        // Verify fast lookups
        List<RunReport> reports = reportDb.getRecentReports(10);
        assertEquals(2, reports.size());
        assertEquals("run-2", reports.get(0).getRunId(), "Reports should be ordered by started_at DESC");

        RunReport fetched = reportDb.getReport("run-1");
        assertNotNull(fetched);
        assertEquals("AuthSuite", fetched.getScopeName());

        // Analytics summary
        ReportDbService.AnalyticsSummary summary = reportDb.getAnalyticsSummary();
        assertEquals(2, summary.getTotalRuns());
        assertEquals(20, summary.getTotalCasesExecuted());
        assertEquals(15, summary.getTotalPassed());
        assertEquals(5, summary.getTotalFailed());
        assertEquals(75.0, summary.getPassRatePercent());
    }

    @Test
    public void testAiChatIndexing() {
        AiChatMessage msg1 = new AiChatMessage("user", "Viết test login");
        msg1.setEstimatedTokens(10);
        indexService.indexChatMessage(msg1, "Auth/LoginSuite");

        AiChatMessage msg2 = new AiChatMessage("assistant", "Đã sinh kịch bản");
        msg2.setEstimatedTokens(25);
        indexService.indexChatMessage(msg2, "Auth/LoginSuite");

        List<AiChatMessage> messages = chatDb.getMessages("Auth/LoginSuite");
        assertEquals(2, messages.size());
        assertEquals("Viết test login", messages.get(0).getContent());
        assertEquals(35, chatDb.calculateTotalTokens("Auth/LoginSuite"));

        // Clear
        indexService.clearChatForSuite("Auth/LoginSuite");
        assertEquals(0, chatDb.getMessages("Auth/LoginSuite").size());
        assertEquals(0, chatDb.calculateTotalTokens("Auth/LoginSuite"));
    }

    @Test
    public void testRecursiveNestedGroupSynchronization(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path parentGroup = tempDir.resolve("ParentGroup");
        java.nio.file.Path childGroup = parentGroup.resolve("ChildGroup");
        java.nio.file.Path nestedSuite = childGroup.resolve("NestedSuite");
        java.nio.file.Files.createDirectories(nestedSuite);

        java.nio.file.Files.writeString(parentGroup.resolve("group.json"), "{\"name\":\"ParentGroup\",\"description\":\"Parent\"}");
        java.nio.file.Files.writeString(childGroup.resolve("group.json"), "{\"name\":\"ChildGroup\",\"description\":\"Child\"}");

        String suiteJson = """
            {
              "name": "NestedSuite",
              "cases": [
                {
                  "id": "tc-nested-1",
                  "name": "NestedCase",
                  "status": "Ready",
                  "assertions": []
                }
              ]
            }
            """;
        java.nio.file.Files.writeString(nestedSuite.resolve("suite.json"), suiteJson);

        indexService.syncProjectDirectory(tempDir);

        assertEquals(1, projectDb.getTotalCasesCount(), "Nested test case must be indexed into H2 database");
        List<TestCase> found = projectDb.searchCases("NestedCase", null);
        assertEquals(1, found.size());
        assertEquals("NestedCase", found.get(0).getName());
        assertEquals("Ready", found.get(0).getStatus());

        List<TestCase> suiteCases = projectDb.getCasesForSuite("ParentGroup/ChildGroup", "NestedSuite");
        assertEquals(1, suiteCases.size());
        assertEquals("NestedCase", suiteCases.get(0).getName());
    }

    @Test
    public void testMtimeCacheSkipsUnmodifiedFilesOnResync(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path groupDir = tempDir.resolve("MtimeGroup");
        java.nio.file.Files.createDirectories(groupDir);
        java.nio.file.Files.writeString(groupDir.resolve("group.json"), "{\"name\":\"MtimeGroup\",\"description\":\"Desc\"}");

        java.nio.file.Path suiteDir = groupDir.resolve("MtimeSuite");
        java.nio.file.Files.createDirectories(suiteDir);
        java.nio.file.Files.writeString(suiteDir.resolve("suite.json"), """
            {
              "name": "MtimeSuite",
              "cases": [
                {
                  "id": "tc-mtime-1",
                  "name": "MtimeCase",
                  "status": "Ready",
                  "assertions": []
                }
              ]
            }
            """);

        // First sync indexes the case
        indexService.syncProjectDirectory(tempDir);
        assertEquals(1, projectDb.getTotalCasesCount());

        // Second sync should safely complete without errors
        indexService.syncProjectDirectory(tempDir);
        assertEquals(1, projectDb.getTotalCasesCount());

        // Modify file -> MTime increases -> resync updates case
        java.nio.file.Files.writeString(suiteDir.resolve("suite.json"), """
            {
              "name": "MtimeSuite",
              "cases": [
                {
                  "id": "tc-mtime-1",
                  "name": "MtimeCaseUpdated",
                  "status": "Ready",
                  "assertions": []
                }
              ]
            }
            """);
        java.nio.file.Files.setLastModifiedTime(suiteDir.resolve("suite.json"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));

        indexService.syncProjectDirectory(tempDir);
        assertEquals(1, projectDb.getTotalCasesCount());
        List<TestCase> updated = projectDb.searchCases("MtimeCaseUpdated", null);
        assertEquals(1, updated.size());
    }
}
