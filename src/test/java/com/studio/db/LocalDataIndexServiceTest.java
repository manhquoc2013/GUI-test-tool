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
        group.setId("grp-auth");
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
        RunReport r1 = new RunReport("run-1", "AuthSuite", "All", 1000L);
        r1.setDurationMs(2000L);
        r1.setTotalCases(10);
        r1.setPassedCases(10);
        r1.setFailedCases(0);
        r1.setSkippedCases(0);
        r1.setStatus("PASSED");
        indexService.indexReport(r1);

        RunReport r2 = new RunReport("run-2", "CartSuite", "All", 2000L);
        r2.setDurationMs(4000L);
        r2.setTotalCases(10);
        r2.setPassedCases(5);
        r2.setFailedCases(5);
        r2.setSkippedCases(0);
        r2.setStatus("FAILED");
        indexService.indexReport(r2);

        // Verify fast lookups
        List<RunReport> reports = reportDb.getRecentReports(10);
        assertEquals(2, reports.size());
        assertEquals("run-2", reports.get(0).getRunId(), "Reports should be ordered by start_time DESC");

        RunReport fetched = reportDb.getReport("run-1");
        assertNotNull(fetched);
        assertEquals("AuthSuite", fetched.getSuiteName());

        // Analytics summary
        ReportDbService.AnalyticsSummary summary = reportDb.getAnalyticsSummary();
        assertEquals(2, summary.getTotalRuns());
        assertEquals(20, summary.getTotalCasesExecuted());
        assertEquals(15, summary.getTotalPassed());
        assertEquals(5, summary.getTotalFailed());
        assertEquals(75.0, summary.getPassRatePercent());
        assertEquals(3000L, summary.getAverageDurationMs());
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
}
