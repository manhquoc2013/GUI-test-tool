package com.studio.service;

import com.studio.model.*;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.store.StepGroupStore;
import com.studio.store.TestDataProfileStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestRunnerProfileVariableIntegrationTest {

    private Path tempDir;
    private ProjectStore projectStore;
    private ReportStore reportStore;
    private SettingsStore settingsStore;
    private StepGroupStore stepGroupStore;
    private TestDataProfileStore profileStore;
    private TestRunnerService runnerService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test-runner-profile-test-");
        Path projectDir = tempDir.resolve("project");
        Path reportsDir = tempDir.resolve("reports");
        Path stepGroupsDir = tempDir.resolve("step-groups");
        Path testDataDir = tempDir.resolve("test-data");
        Files.createDirectories(projectDir);
        Files.createDirectories(reportsDir);
        Files.createDirectories(stepGroupsDir);
        Files.createDirectories(testDataDir);

        projectStore = new ProjectStore();
        reportStore = new ReportStore();
        settingsStore = new SettingsStore();
        stepGroupStore = new StepGroupStore(stepGroupsDir);
        profileStore = new TestDataProfileStore(testDataDir);

        runnerService = new TestRunnerService(projectStore, reportStore, settingsStore, null, stepGroupStore,
                profileStore);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void testProfileVariablesPassedAndOverriddenByCaseData() throws IOException {
        // 1. Create a TestDataProfile with base environment variables
        TestDataProfile profile = new TestDataProfile("tdp_env", "Staging Environment");
        profile.addVariable("BASE_URL", "https://staging.test.com");
        profile.addVariable("TIMEOUT", "5000");
        profile.addVariable("USER_NAME", "global_admin");
        profileStore.save(profile);

        // 2. Prepare case steps with variables
        TestStep step1 = new TestStep(1, "NAVIGATE", "{{BASE_URL}}/dashboard", "", "Go to dashboard", "");
        TestStep step2 = new TestStep(2, "FILL", "#user", "{{USER_NAME}}", "Fill username", "");

        // 3. Prepare assertion with variable
        Assertion a1 = new Assertion();
        a1.setType("TEXT_VISIBLE");
        a1.setValue("Welcome {{USER_NAME}}");

        // Case-specific override: overrides USER_NAME from global_admin to local_tester
        Map<String, String> caseParams = Map.of("USER_NAME", "local_tester");

        Map<String, String> activeVars = new java.util.HashMap<>(profile.getVariables());
        activeVars.putAll(caseParams);

        String expandedStep1 = com.studio.util.VariableResolver.resolve(step1.getTarget(), activeVars);
        String expandedStep2 = com.studio.util.VariableResolver.resolve(step2.getTarget(), activeVars);
        List<Assertion> resolvedAsserts = com.studio.util.VariableResolver.resolveAssertions(List.of(a1), activeVars);

        assertEquals("https://staging.test.com/dashboard", expandedStep1);
        assertEquals("#user", expandedStep2);
        assertEquals("Welcome local_tester", resolvedAsserts.get(0).getValue());
    }
}
