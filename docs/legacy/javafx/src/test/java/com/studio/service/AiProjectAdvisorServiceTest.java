package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestGroup;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiProjectAdvisorServiceTest {

    @TempDir
    Path tempDir;

    private ProjectStore projectStore;
    private AiProjectAdvisorService advisorService;

    @BeforeEach
    void setUp() throws IOException {
        projectStore = new ProjectStore();
        advisorService = new AiProjectAdvisorService(projectStore);
    }

    @Test
    void testEvaluateCase_DetectsFlakySleepAndMissingAssertions() throws IOException {
        Path suiteDir = tempDir.resolve("FlakySuite");
        Files.createDirectories(suiteDir);

        TestSuite suite = new TestSuite("FlakySuite", "Suite with flaky case");
        TestCase tc = new TestCase("tc1", "TC_Flaky", "Flaky test description", "TC_Flaky.java", "ACTIVE");
        tc.setAssertions(new ArrayList<>()); // zero assertions!
        suite.setCases(List.of(tc));

        new ObjectMapper().writeValue(suiteDir.resolve("suite.json").toFile(), suite);

        // Script has Thread.sleep
        String code = """
                package com.test;
                public class TC_Flaky {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(5000);
                        // No assertions
                    }
                }
                """;
        Files.writeString(suiteDir.resolve("TC_Flaky.java"), code);

        AiProjectAdvisorService.EvaluationResult eval = advisorService.evaluateCase("TC_Flaky", suiteDir);
        assertNotNull(eval);
        assertEquals("TC_Flaky", eval.caseName());
        assertTrue(eval.score() < 50, "Score should be penalised heavily for sleep + zero assertions, got: " + eval.score());
        assertTrue(eval.grade().contains("D") || eval.grade().contains("C"));
        assertFalse(eval.criticalFlaws().isEmpty(), "Should have critical flaws recorded");
        assertTrue(eval.markdownReport().contains("Thread.sleep"));
    }

    @Test
    void testEvaluateCase_HighQualityDeterministicCase() throws IOException {
        Path suiteDir = tempDir.resolve("CleanSuite");
        Files.createDirectories(suiteDir);

        TestSuite suite = new TestSuite("CleanSuite", "High quality suite");
        TestCase tc = new TestCase("tc2", "TC_Clean", "Login valid test", "TC_Clean.java", "ACTIVE");
        Assertion a1 = new Assertion();
        a1.setType("TITLE_CONTAINS");
        a1.setValue("Dashboard");
        Assertion a2 = new Assertion();
        a2.setType("ELEMENT_VISIBLE");
        a2.setLocator("[data-testid='welcome']");
        tc.setAssertions(List.of(a1, a2));
        suite.setCases(List.of(tc));

        new ObjectMapper().writeValue(suiteDir.resolve("suite.json").toFile(), suite);

        String code = """
                package com.test;
                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
                public class TC_Clean {
                    public static void main(String[] args) {
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
                        assertThat(page.locator("[data-testid='welcome']")).isVisible();
                    }
                }
                """;
        Files.writeString(suiteDir.resolve("TC_Clean.java"), code);

        AiProjectAdvisorService.EvaluationResult eval = advisorService.evaluateCase("TC_Clean", suiteDir);
        assertNotNull(eval);
        assertTrue(eval.score() >= 85, "Clean case should get high score, got: " + eval.score());
        assertTrue(eval.grade().contains("A"));
        assertTrue(eval.criticalFlaws().isEmpty());
    }

    @Test
    void testAnalyzeSuiteGaps_IdentifiesMissingNegativeAndBoundary() throws IOException {
        Path suiteDir = tempDir.resolve("SingleHappySuite");
        Files.createDirectories(suiteDir);

        TestSuite suite = new TestSuite("SingleHappySuite", "Only happy path");
        TestCase tc = new TestCase("tc3", "Login_Success_Valid", "Đăng nhập thành công với tài khoản hợp lệ", "Login_Success_Valid.java", "ACTIVE");
        suite.setCases(List.of(tc));

        new ObjectMapper().writeValue(suiteDir.resolve("suite.json").toFile(), suite);

        AiProjectAdvisorService.GapAnalysisResult gapResult = advisorService.analyzeSuiteGaps(suiteDir);
        assertNotNull(gapResult);
        assertEquals(1, gapResult.totalCases());
        assertFalse(gapResult.missingGaps().isEmpty());
        assertTrue(gapResult.markdownReport().contains("Negative Validation"));
        assertTrue(gapResult.markdownReport().contains("Boundary Value"));
    }

    @Test
    void testAnalyzeSuiteGaps_NullSuiteReturnsFriendlyPrompt() {
        AiProjectAdvisorService.GapAnalysisResult gapResult = advisorService.analyzeSuiteGaps(null);
        assertNotNull(gapResult);
        assertTrue(gapResult.markdownReport().contains("Vui lòng chọn một Test Suite"));
    }

    @Test
    void testSearchCases_WithDirectAndNestedStructure() throws IOException {
        Path groupDir = tempDir.resolve("AuthGroup");
        Files.createDirectories(groupDir);
        TestGroup group = new TestGroup("AuthGroup", "Group description");
        new ObjectMapper().writeValue(groupDir.resolve("group.json").toFile(), group);

        Path suiteDir = groupDir.resolve("LoginSuite");
        Files.createDirectories(suiteDir);
        TestSuite suite = new TestSuite("LoginSuite", "Suite description");
        TestCase tc = new TestCase("tc_search", "Search_Target_Case", "Case to search", "Search_Target_Case.java", "ACTIVE");
        tc.setStartUrl("https://example.com/login");
        suite.setCases(List.of(tc));
        new ObjectMapper().writeValue(suiteDir.resolve("suite.json").toFile(), suite);

        String code = """
                package com.test;
                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
                public class Search_Target_Case {
                    public static void main(String[] args) {
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
                        assertThat(page.locator("#dashboard")).isVisible();
                    }
                }
                """;
        Files.writeString(suiteDir.resolve("Search_Target_Case.java"), code);

        AiProjectAdvisorService.SearchResult res = advisorService.searchCases("Search_Target", tempDir);
        assertNotNull(res);
        assertFalse(res.matches().isEmpty());
        assertEquals("Search_Target_Case", res.matches().get(0).caseName());
        assertTrue(res.matches().get(0).suiteDir().endsWith("AuthGroup/LoginSuite") || res.matches().get(0).suiteDir().endsWith("AuthGroup\\LoginSuite"));
        assertTrue(Files.exists(res.matches().get(0).suiteDir()));

        // Test global evaluateCase without suiteDir, relying on global tree search
        AiProjectAdvisorService.EvaluationResult globalEval = advisorService.evaluateCase("Search_Target_Case", null, tempDir);
        assertNotNull(globalEval);
        assertEquals("Search_Target_Case", globalEval.caseName());
        assertTrue(globalEval.score() >= 70);
    }
}
