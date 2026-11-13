package com.studio.service;

import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.model.ExportLanguage;
import com.studio.model.TestCase;
import com.studio.model.TestDataItem;
import com.studio.model.TestStep;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import com.studio.util.AssertTypeDisplay;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.studio.util.StringEscapeUtil;

/**
 * Service to export Test Cases from a Suite or Group to a professional Excel
 * workbook
 * compliant with international QA / ISTQB / IEEE 829 Test Case Specification
 * standards.
 */
public final class TestCaseExcelExportService {

    private static final byte[] NAVY = hex("1F2A44");
    private static final byte[] NAVY_SOFT = hex("2A3654");
    private static final byte[] HEADER_BG = hex("1E3A8A"); // Deep Royal Blue for tables
    private static final byte[] LABEL_BG = hex("F1F5F9");
    private static final byte[] ZEBRA = hex("F8FAFC");
    private static final byte[] READY_BG = hex("DCFCE7");
    private static final byte[] READY_FG = hex("166534");
    private static final byte[] DRAFT_BG = hex("FEF3C7");
    private static final byte[] DRAFT_FG = hex("92400E");
    private static final byte[] P1_BG = hex("FEE2E2");
    private static final byte[] P1_FG = hex("991B1B");
    private static final byte[] P2_BG = hex("E0F2FE");
    private static final byte[] P2_FG = hex("0369A1");
    private static final byte[] P3_BG = hex("F1F5F9");
    private static final byte[] P3_FG = hex("475569");
    private static final byte[] BORDER_COLOR = hex("CBD5E1");

    private static final DateTimeFormatter DISPLAY_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int[] CASES_WIDTHS = {
            7, 18, 30, 18, 18, 14, 14, 28, 44, 26, 38, 22
    };

    private static final int[] STEPS_WIDTHS = {
            7, 18, 28, 10, 18, 28, 24, 38, 34
    };

    private TestCaseExcelExportService() {
    }

    public static final class SuiteContainer {
        private final TestSuite suite;
        private final String groupPath;
        private final Path suiteDir;

        public SuiteContainer(TestSuite suite, String groupPath, Path suiteDir) {
            this.suite = suite;
            this.groupPath = groupPath;
            this.suiteDir = suiteDir;
        }

        public TestSuite getSuite() {
            return suite;
        }

        public String getGroupPath() {
            return groupPath;
        }

        public Path getSuiteDir() {
            return suiteDir;
        }
    }

    public static final class TestCaseRowData {
        public int index;
        public String id;
        public String name;
        public String groupPath;
        public String suiteName;
        public String status;
        public String priority;
        public String preconditions;
        public String stepsSummary;
        public String testDataSummary;
        public String expectedResults;
        public String scriptFile;
        public String description;
        public List<TestStep> steps = new ArrayList<>();
    }

    /**
     * Export all test cases from a single TestSuite.
     */
    public static void exportSuite(TestSuite suite, String groupName, Path suiteDir, Path outputPath) throws Exception {
        exportSuite(suite, groupName, suiteDir, outputPath, ExportLanguage.VIETNAMESE);
    }

    public static void exportSuite(TestSuite suite, String groupName, Path suiteDir, Path outputPath,
            ExportLanguage lang) throws Exception {
        ExportLanguage effective = (lang != null) ? lang : ExportLanguage.VIETNAMESE;
        List<SuiteContainer> suites = new ArrayList<>();
        suites.add(
                new SuiteContainer(suite, groupName != null && !groupName.isBlank() ? groupName : "Project", suiteDir));
        String sName = suite.getName() != null && !suite.getName().isBlank() ? suite.getName() : "Suite";
        String scopeTitle = I18nService.get(effective, "export.common.scope.suite", sName);
        String scopeDesc = I18nService.get(effective, "export.common.scope.suite_desc", sName);
        exportSuites(scopeTitle, scopeDesc, suites, outputPath, effective);
    }

    /**
     * Export all test cases in a group and its nested sub-groups/suites.
     */
    public static void exportGroup(Path groupDir, ProjectStore store, Path outputPath) throws Exception {
        exportGroup(groupDir, store, outputPath, ExportLanguage.VIETNAMESE);
    }

    public static void exportGroup(Path groupDir, ProjectStore store, Path outputPath, ExportLanguage lang)
            throws Exception {
        ExportLanguage effective = (lang != null) ? lang : ExportLanguage.VIETNAMESE;
        List<SuiteContainer> suites = new ArrayList<>();
        String rootName = groupDir.getFileName() != null ? groupDir.getFileName().toString() : "Group";
        collectSuitesInDir(groupDir, rootName, store, suites);
        String scopeTitle = I18nService.get(effective, "export.common.scope.group", rootName);
        String scopeDesc = I18nService.get(effective, "export.common.scope.group_desc", rootName);
        exportSuites(scopeTitle, scopeDesc, suites, outputPath, effective);
    }

    private static void collectSuitesInDir(Path dir, String currentGroupPath, ProjectStore store,
            List<SuiteContainer> result) {
        if (dir == null || !Files.isDirectory(dir))
            return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry))
                    continue;
                if (Files.exists(entry.resolve("suite.json"))) {
                    try {
                        TestSuite s = store.loadAndMigrateSuite(entry);
                        result.add(new SuiteContainer(s, currentGroupPath, entry));
                    } catch (Exception ignored) {
                    }
                } else if (Files.exists(entry.resolve("group.json")) || Files.isDirectory(entry)) {
                    String subPath = currentGroupPath.isEmpty()
                            ? entry.getFileName().toString()
                            : currentGroupPath + " / " + entry.getFileName().toString();
                    collectSuitesInDir(entry, subPath, store, result);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Core generator that creates the standard 3-sheet Excel workbook.
     */
    public static void exportSuites(String scopeTitle, String scopeDescription, List<SuiteContainer> suites,
            Path outputPath) throws Exception {
        exportSuites(scopeTitle, scopeDescription, suites, outputPath, ExportLanguage.VIETNAMESE);
    }

    public static void exportSuites(String scopeTitle, String scopeDescription, List<SuiteContainer> suites,
            Path outputPath, ExportLanguage lang) throws Exception {
        ExportLanguage effective = (lang != null) ? lang : ExportLanguage.VIETNAMESE;
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        // Flatten all test cases into row data
        List<TestCaseRowData> allCases = new ArrayList<>();
        int caseIndex = 1;
        for (SuiteContainer sc : suites) {
            TestSuite suite = sc.getSuite();
            if (suite.getCases() == null)
                continue;
            for (TestCase tc : suite.getCases()) {
                allCases.add(
                        buildRowData(caseIndex++, tc, sc.getGroupPath(), suite.getName(), sc.getSuiteDir(), effective));
            }
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Styles styles = Styles.create(wb);

            // Sheet 1: Tổng quan / Overview
            String summarySheetName = I18nService.get(effective, "export.tc.sheet.overview");
            Sheet summary = wb.createSheet(summarySheetName);
            createSummarySheet(summary, scopeTitle, scopeDescription, suites, allCases, styles, effective);
            tintSheet(summary, IndexedColors.DARK_BLUE);

            // Sheet 2: Danh sách Test Cases / Test Cases
            String casesSheetName = I18nService.get(effective, "export.tc.sheet.cases");
            Sheet casesSheet = wb.createSheet(casesSheetName);
            createCasesSheet(casesSheet, allCases, styles, effective);
            tintSheet(casesSheet, IndexedColors.DARK_TEAL);

            // Sheet 3: Chi tiết các bước / Step Details
            String stepsSheetName = I18nService.get(effective, "export.tc.sheet.steps");
            Sheet stepsSheet = wb.createSheet(stepsSheetName);
            createStepsSheet(stepsSheet, allCases, styles, effective);
            tintSheet(stepsSheet, IndexedColors.GREY_50_PERCENT);

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        }
        try {
            String grp = (suites != null && !suites.isEmpty()) ? suites.get(0).getGroupPath() : "";
            String ste = (suites != null && !suites.isEmpty() && suites.get(0).getSuite() != null)
                    ? suites.get(0).getSuite().getName() : scopeTitle;
            new AiAgentIndexService().archiveAndIndexExport(outputPath, "EXCEL_SUITE", grp, ste, allCases.size());
        } catch (Exception ignored) {
        }
        ResourceReleaseService.releaseAfterExport();
    }

    /**
     * Export a list of AI-generated test cases directly to an Excel workbook.
     */
    public static void exportAiGeneratedCases(List<AiGeneratedCase> cases, String promptSummary, Path outputPath)
            throws Exception {
        exportAiGeneratedCases(cases, promptSummary, outputPath, ExportLanguage.VIETNAMESE);
    }

    public static void exportAiGeneratedCases(List<AiGeneratedCase> cases, String promptSummary, Path outputPath,
            ExportLanguage lang) throws Exception {
        ExportLanguage effective = (lang != null) ? lang : ExportLanguage.VIETNAMESE;
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        List<TestCaseRowData> allCases = new ArrayList<>();
        int caseIndex = 1;
        if (cases != null) {
            for (AiGeneratedCase ac : cases) {
                allCases.add(buildRowDataFromAiCase(caseIndex++, ac, "AI Generated Suite", effective));
            }
        }

        String scopeTitle = "AI Assistant Generated Cases";
        String scopeDesc = promptSummary != null && !promptSummary.isBlank() ? promptSummary
                : "Bộ Test Case sinh tự động từ Trợ lý AI";

        try (Workbook wb = new XSSFWorkbook()) {
            Styles styles = Styles.create(wb);

            // Sheet 1: Tổng quan / Overview
            String summarySheetName = I18nService.get(effective, "export.tc.sheet.overview");
            Sheet summary = wb.createSheet(summarySheetName);
            List<SuiteContainer> dummySuites = List.of(
                    new SuiteContainer(new TestSuite("AI Generated Suite", "AI Assistant"), "AI Assistant", null));
            createSummarySheet(summary, scopeTitle, scopeDesc, dummySuites, allCases, styles, effective);
            tintSheet(summary, IndexedColors.DARK_BLUE);

            // Sheet 2: Danh sách Test Cases / Test Cases
            String casesSheetName = I18nService.get(effective, "export.tc.sheet.cases");
            Sheet casesSheet = wb.createSheet(casesSheetName);
            createCasesSheet(casesSheet, allCases, styles, effective);
            tintSheet(casesSheet, IndexedColors.DARK_TEAL);

            // Sheet 3: Chi tiết các bước / Step Details
            String stepsSheetName = I18nService.get(effective, "export.tc.sheet.steps");
            Sheet stepsSheet = wb.createSheet(stepsSheetName);
            createStepsSheet(stepsSheet, allCases, styles, effective);
            tintSheet(stepsSheet, IndexedColors.GREY_50_PERCENT);

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        }
        try {
            new AiAgentIndexService().archiveAndIndexExport(outputPath, "EXCEL_AI", "AI Assistant", scopeTitle, allCases.size());
        } catch (Exception ignored) {
        }
        ResourceReleaseService.releaseAfterExport();
    }

    public static TestCaseRowData buildRowDataFromAiCase(int index, AiGeneratedCase ac, String suiteName,
            ExportLanguage lang) {
        TestCaseRowData row = new TestCaseRowData();
        row.index = index;
        row.id = ac.getTargetCaseId() != null && !ac.getTargetCaseId().isBlank()
                ? ac.getTargetCaseId()
                : String.format("TC_%03d", index);
        row.name = ac.getName() != null && !ac.getName().isBlank() ? ac.getName() : "Case " + index;
        row.groupPath = "AI Generator";
        row.suiteName = suiteName != null && !suiteName.isBlank() ? suiteName : "Generated Cases";
        row.status = "Ready";
        row.priority = com.studio.model.TestPriority.getLabel(ac.getPriority());

        // Preconditions
        if (ac.getPreconditions() != null && !ac.getPreconditions().isEmpty()) {
            row.preconditions = String.join("\n", ac.getPreconditions());
        } else if (ac.getStartUrl() != null && !ac.getStartUrl().isBlank()) {
            row.preconditions = I18nService.get(lang, "export.tc.preconditions.url", ac.getStartUrl());
        } else {
            row.preconditions = I18nService.get(lang, "export.tc.preconditions.default");
        }

        row.scriptFile = ac.getScriptCode() != null && !ac.getScriptCode().isBlank() ? "Playwright Script" : "";
        row.description = ac.getDescription() != null ? ac.getDescription() : "";

        // Steps
        List<TestStep> steps = ac.getTestSteps();
        if (steps != null && !steps.isEmpty()) {
            row.steps.addAll(steps);
            StringBuilder sb = new StringBuilder();
            StringBuilder dataSb = new StringBuilder();
            StringBuilder expSb = new StringBuilder();
            int stepIdx = 1;
            for (TestStep st : steps) {
                String act = st.getAction() != null ? st.getAction() : "";
                String desc = st.getDescription() != null && !st.getDescription().isBlank()
                        ? st.getDescription()
                        : (act + " " + (st.getTarget() != null ? st.getTarget() : "")).trim();
                sb.append(stepIdx).append(". ").append(desc).append("\n");
                if (st.getTestData() != null && !st.getTestData().isBlank()) {
                    dataSb.append(I18nService.get(lang, "export.tc.steps.step_prefix", stepIdx, st.getTestData()))
                            .append("\n");
                }
                if (st.getExpectedResult() != null && !st.getExpectedResult().isBlank()) {
                    expSb.append(I18nService.get(lang, "export.tc.steps.step_prefix", stepIdx, st.getExpectedResult()))
                            .append("\n");
                }
                stepIdx++;
            }
            row.stepsSummary = sb.toString().trim();
            row.testDataSummary = dataSb.toString().trim();
            row.expectedResults = expSb.toString().trim();
        }

        // Format Assertions into Expected Results if any
        if (ac.getAssertions() != null && !ac.getAssertions().isEmpty()) {
            StringBuilder assertSb = new StringBuilder();
            if (row.expectedResults != null && !row.expectedResults.isBlank()) {
                assertSb.append(row.expectedResults).append("\n\nTiêu chí xác thực (Assertions):\n");
            }
            int aIdx = 1;
            for (Assertion a : ac.getAssertions()) {
                String label = AssertTypeDisplay.label(a.getType());
                String loc = a.getLocator() != null && !a.getLocator().isBlank() ? (" [" + a.getLocator() + "]") : "";
                String val = a.getValue() != null && !a.getValue().isBlank() ? (" = \"" + a.getValue() + "\"") : "";
                assertSb.append(aIdx++).append(". ").append(label).append(loc).append(val).append("\n");
            }
            row.expectedResults = assertSb.toString().trim();
        }

        // Test Data Sets
        if (ac.getTestDataSets() != null && !ac.getTestDataSets().isEmpty()) {
            StringBuilder dsSb = new StringBuilder();
            if (row.testDataSummary != null && !row.testDataSummary.isBlank()) {
                dsSb.append(row.testDataSummary).append("\n\n");
            }
            dsSb.append("Dữ liệu kiểm thử tham số hóa:\n");
            for (TestDataItem item : ac.getTestDataSets()) {
                dsSb.append("• ").append(item.getScenario()).append(": ");
                if (item.getParameters() != null) {
                    item.getParameters().forEach((k, v) -> dsSb.append(k).append("=").append(v).append("; "));
                }
                dsSb.append("-> ").append(item.getExpectedOutcome()).append("\n");
            }
            row.testDataSummary = dsSb.toString().trim();
        }

        if (row.expectedResults == null || row.expectedResults.isBlank()) {
            row.expectedResults = I18nService.get(lang, "export.common.default_expected");
        }
        if (row.testDataSummary == null || row.testDataSummary.isBlank()) {
            row.testDataSummary = I18nService.get(lang, "export.common.default_data");
        }

        return row;
    }

    /**
     * Export a list of AI-generated test cases to a formatted Markdown document.
     */
    public static void exportToMarkdown(List<AiGeneratedCase> cases, String promptSummary, Path outputPath)
            throws Exception {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# BỘ TEST CASE KIỂM THỬ TỰ ĐỘNG HÓA\n\n");
        sb.append("**Mô tả / Tóm tắt:** ").append(promptSummary != null ? promptSummary : "Sinh tự động từ Trợ lý AI")
                .append("\n\n");
        sb.append("**Thời gian tạo:** ").append(DISPLAY_DT.format(LocalDateTime.now())).append("\n\n");
        sb.append("**Tổng số test cases:** ").append(cases != null ? cases.size() : 0).append("\n\n");
        sb.append("---\n\n");

        if (cases != null) {
            int idx = 1;
            for (AiGeneratedCase c : cases) {
                sb.append("## ").append(idx++).append(". ").append(c.getName()).append("\n\n");
                sb.append("- **Mức độ ưu tiên:** ").append(com.studio.model.TestPriority.getLabel(c.getPriority()))
                        .append("\n");
                if (c.getDescription() != null && !c.getDescription().isBlank()) {
                    sb.append("- **Mục tiêu:** ").append(c.getDescription()).append("\n");
                }
                if (c.getStartUrl() != null && !c.getStartUrl().isBlank()) {
                    sb.append("- **URL kiểm thử:** ").append(c.getStartUrl()).append("\n");
                }
                if (c.getPreconditions() != null && !c.getPreconditions().isEmpty()) {
                    sb.append("- **Tiền điều kiện:**\n");
                    for (String pre : c.getPreconditions()) {
                        sb.append("  * ").append(pre).append("\n");
                    }
                }
                sb.append("\n");

                // Steps table
                if (c.getTestSteps() != null && !c.getTestSteps().isEmpty()) {
                    sb.append("### Các bước kiểm thử (Test Steps):\n\n");
                    sb.append("| STT | Hành động | Đối tượng (Target) | Dữ liệu (Test Data) | Mô tả bước | Kết quả kỳ vọng |\n");
                    sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
                    int sNum = 1;
                    for (TestStep st : c.getTestSteps()) {
                        int num = st.getStepNumber() > 0 ? st.getStepNumber() : sNum++;
                        sb.append("| ").append(num)
                                .append(" | ").append(escapeMd(st.getAction()))
                                .append(" | ").append(escapeMd(st.getTarget()))
                                .append(" | ").append(escapeMd(st.getTestData()))
                                .append(" | ").append(escapeMd(st.getDescription()))
                                .append(" | ").append(escapeMd(st.getExpectedResult()))
                                .append(" |\n");
                    }
                    sb.append("\n");
                }

                // Assertions
                if (c.getAssertions() != null && !c.getAssertions().isEmpty()) {
                    sb.append("### Tiêu chí xác thực (Assertions):\n\n");
                    int aIdx = 1;
                    for (Assertion a : c.getAssertions()) {
                        sb.append(aIdx++).append(". `").append(a.getType()).append("`");
                        if (a.getLocator() != null && !a.getLocator().isBlank()) {
                            sb.append(" trên phần tử `").append(a.getLocator()).append("`");
                        }
                        if (a.getValue() != null && !a.getValue().isBlank()) {
                            sb.append(" với giá trị: \"").append(a.getValue()).append("\"");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }

                // Test Data Sets
                if (c.getTestDataSets() != null && !c.getTestDataSets().isEmpty()) {
                    sb.append("### Bộ dữ liệu kiểm thử biên & phân vùng (Test Data Sets):\n\n");
                    for (TestDataItem ds : c.getTestDataSets()) {
                        sb.append("- **Kịch bản:** ").append(ds.getScenario()).append("\n");
                        if (ds.getParameters() != null && !ds.getParameters().isEmpty()) {
                            sb.append("  - Tham số: `").append(ds.getParameters().toString()).append("`\n");
                        }
                        sb.append("  - Kết quả kỳ vọng: ").append(ds.getExpectedOutcome()).append("\n");
                    }
                    sb.append("\n");
                }

                if (c.getScriptCode() != null && !c.getScriptCode().isBlank()) {
                    sb.append("### Kịch bản tự động hóa Playwright Java:\n\n```java\n");
                    sb.append(c.getScriptCode().trim());
                    sb.append("\n```\n\n");
                }
                sb.append("---\n\n");
            }
        }
        Files.writeString(outputPath, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            new AiAgentIndexService().archiveAndIndexExport(outputPath, "MARKDOWN", "AI Assistant", "AI Generated Suite",
                    cases != null ? cases.size() : 0);
        } catch (Exception ignored) {
        }
    }

    /**
     * Export a list of AI-generated test cases to a structured JSON file.
     */
    public static void exportToJson(List<AiGeneratedCase> cases, String promptSummary, Path outputPath)
            throws Exception {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", "AI Generated Test Suite");
        root.put("summary", promptSummary != null ? promptSummary : "");
        root.put("exportedAt", DISPLAY_DT.format(LocalDateTime.now()));
        root.put("totalCases", cases != null ? cases.size() : 0);
        root.put("cases", cases != null ? cases : List.of());
        mapper.writeValue(outputPath.toFile(), root);
        try {
            new AiAgentIndexService().archiveAndIndexExport(outputPath, "JSON", "AI Assistant", "AI Generated Suite",
                    cases != null ? cases.size() : 0);
        } catch (Exception ignored) {
        }
    }

    /**
     * Export runnable Playwright Java test scripts to a ZIP archive.
     */
    public static int exportScriptsToZip(List<AiGeneratedCase> cases, Path outputPath) throws Exception {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath))) {
            int idx = 1;
            if (cases != null) {
                for (AiGeneratedCase ac : cases) {
                    if (ac.getScriptCode() != null && !ac.getScriptCode().isBlank()) {
                        String name = StringEscapeUtil.safeFileName(ac.getName(), "TC_" + String.format("%02d", idx));
                        ZipEntry entry = new ZipEntry(name + ".java");
                        zos.putNextEntry(entry);
                        zos.write(ac.getScriptCode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        zos.closeEntry();
                        count++;
                        idx++;
                    }
                }
            }
        }
        try {
            new AiAgentIndexService().archiveAndIndexExport(outputPath, "SCRIPTS_ZIP", "AI Assistant", "AI Generated Suite", count);
        } catch (Exception ignored) {
        }
        return count;
    }

    /**
     * Export BOTH Test Scenarios (QA Excel/Markdown/JSON specification) AND runnable
     * Playwright Java automation scripts bundled together into a ZIP archive.
     */
    public static void exportFullBundle(List<AiGeneratedCase> cases, String promptSummary, Path outputPath)
            throws Exception {
        exportFullBundle(cases, promptSummary, outputPath, ExportLanguage.VIETNAMESE);
    }

    public static void exportFullBundle(List<AiGeneratedCase> cases, String promptSummary, Path outputPath,
                                        ExportLanguage lang) throws Exception {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        ExportLanguage effective = (lang != null) ? lang : ExportLanguage.VIETNAMESE;
        Path tempDir = Files.createTempDirectory("studio_bundle_");
        try {
            Path excelPath = tempDir.resolve("TestScenarios_ISTQB.xlsx");
            exportAiGeneratedCases(cases, promptSummary, excelPath, effective);

            Path mdPath = tempDir.resolve("TestScenarios_Specification.md");
            exportToMarkdown(cases, promptSummary, mdPath);

            Path jsonPath = tempDir.resolve("TestScenarios_Data.json");
            exportToJson(cases, promptSummary, jsonPath);

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath))) {
                // Add QA Scenario documents under scenarios/
                addFileEntryToZip(zos, excelPath, "scenarios/TestScenarios_ISTQB.xlsx");
                addFileEntryToZip(zos, mdPath, "scenarios/TestScenarios_Specification.md");
                addFileEntryToZip(zos, jsonPath, "scenarios/TestScenarios_Data.json");

                // Add Runnable Java Scripts under scripts/
                int idx = 1;
                if (cases != null) {
                    for (AiGeneratedCase ac : cases) {
                        if (ac.getScriptCode() != null && !ac.getScriptCode().isBlank()) {
                            String name = StringEscapeUtil.safeFileName(ac.getName(), "TC_" + String.format("%02d", idx));
                            ZipEntry entry = new ZipEntry("scripts/" + name + ".java");
                            zos.putNextEntry(entry);
                            zos.write(ac.getScriptCode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            zos.closeEntry();
                            idx++;
                        }
                    }
                }
            }
        } finally {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
        try {
            new AiAgentIndexService().archiveAndIndexExport(outputPath, "FULL_BUNDLE_ZIP", "AI Assistant", "AI Generated Suite",
                    cases != null ? cases.size() : 0);
        } catch (Exception ignored) {
        }
        ResourceReleaseService.releaseAfterExport();
    }

    private static void addFileEntryToZip(ZipOutputStream zos, Path filePath, String entryName) throws Exception {
        if (Files.exists(filePath)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            Files.copy(filePath, zos);
            zos.closeEntry();
        }
    }

    private static String escapeMd(String text) {
        if (text == null)
            return "";
        return text.replace("|", "\\|").replace("\r", "").replace("\n", " ");
    }

    private static TestCaseRowData buildRowData(int index, TestCase tc, String groupPath, String suiteName,
            Path suiteDir) {
        return buildRowData(index, tc, groupPath, suiteName, suiteDir, ExportLanguage.VIETNAMESE);
    }

    private static TestCaseRowData buildRowData(int index, TestCase tc, String groupPath, String suiteName,
            Path suiteDir, ExportLanguage lang) {
        TestCaseRowData row = new TestCaseRowData();
        row.index = index;
        row.id = tc.getId() != null && !tc.getId().isBlank() ? tc.getId() : String.format("TC_%03d", index);
        row.name = tc.getName() != null && !tc.getName().isBlank() ? tc.getName() : "Unnamed Case";
        row.groupPath = groupPath != null && !groupPath.isBlank() ? groupPath : "Root";
        row.suiteName = suiteName != null && !suiteName.isBlank() ? suiteName : "Default Suite";
        row.status = tc.getStatus() != null && !tc.getStatus().isBlank() ? tc.getStatus() : "Draft";
        row.priority = com.studio.model.TestPriority.getLabel(tc.getPriority());
        row.preconditions = tc.getStartUrl() != null && !tc.getStartUrl().isBlank()
                ? I18nService.get(lang, "export.tc.preconditions.url", tc.getStartUrl())
                : I18nService.get(lang, "export.tc.preconditions.default");
        row.scriptFile = tc.getScriptFile() != null ? tc.getScriptFile() : "";
        row.description = tc.getDescription() != null ? tc.getDescription() : "";

        // Collect steps
        List<TestStep> steps = tc.getTestSteps();
        if (steps != null && !steps.isEmpty()) {
            row.steps.addAll(steps);
            StringBuilder sb = new StringBuilder();
            StringBuilder dataSb = new StringBuilder();
            StringBuilder expSb = new StringBuilder();

            int stepIdx = 1;
            for (TestStep st : steps) {
                String act = st.getAction() != null ? st.getAction() : "";
                String desc = st.getDescription() != null && !st.getDescription().isBlank()
                        ? st.getDescription()
                        : (act + " " + st.getTarget());
                sb.append(stepIdx).append(". ").append(desc).append("\n");

                if (st.getTestData() != null && !st.getTestData().isBlank()) {
                    dataSb.append(I18nService.get(lang, "export.tc.steps.step_prefix", stepIdx, st.getTestData()))
                            .append("\n");
                }
                if (st.getExpectedResult() != null && !st.getExpectedResult().isBlank()) {
                    expSb.append(I18nService.get(lang, "export.tc.steps.step_prefix", stepIdx, st.getExpectedResult()))
                            .append("\n");
                }
                stepIdx++;
            }
            row.stepsSummary = sb.toString().trim();
            row.testDataSummary = dataSb.toString().trim();
            if (row.expectedResults == null || row.expectedResults.isBlank()) {
                row.expectedResults = expSb.toString().trim();
            }
        }

        // Fallback: extract steps from comments in script file if testSteps is empty
        if (row.stepsSummary == null || row.stepsSummary.isBlank()) {
            row.stepsSummary = extractStepsFromScript(suiteDir, row.scriptFile, tc.getName(), lang);
        }

        // Format Assertions into Expected Results
        StringBuilder assertSb = new StringBuilder();
        if (tc.getAssertions() != null && !tc.getAssertions().isEmpty()) {
            int aIdx = 1;
            for (Assertion a : tc.getAssertions()) {
                String typeLabel = AssertTypeDisplay.label(a.getType());
                String val = a.getValue() != null ? a.getValue()
                        : (a.getExpectedValue() != null ? a.getExpectedValue() : "");
                String loc = a.getLocator() != null && !a.getLocator().isBlank() ? " [" + a.getLocator() + "]" : "";
                assertSb.append(aIdx++).append(". ").append(typeLabel);
                if (!val.isBlank()) {
                    assertSb.append(": \"").append(val).append("\"");
                }
                assertSb.append(loc).append("\n");
            }
        }

        if (assertSb.length() > 0) {
            if (row.expectedResults != null && !row.expectedResults.isBlank()) {
                row.expectedResults = row.expectedResults + "\n" + assertSb.toString().trim();
            } else {
                row.expectedResults = assertSb.toString().trim();
            }
        }

        if (row.expectedResults == null || row.expectedResults.isBlank()) {
            row.expectedResults = I18nService.get(lang, "export.common.default_expected");
        }
        if (row.testDataSummary == null || row.testDataSummary.isBlank()) {
            row.testDataSummary = I18nService.get(lang, "export.common.default_data");
        }

        return row;
    }

    private static String extractStepsFromScript(Path suiteDir, String scriptFile, String caseName,
            ExportLanguage lang) {
        String fallback = I18nService.get(lang, "export.tc.steps.fallback_execute");
        if (suiteDir == null)
            return fallback;
        Path file = null;
        if (scriptFile != null && !scriptFile.isBlank()) {
            file = suiteDir.resolve(scriptFile);
        } else if (caseName != null && !caseName.isBlank()) {
            file = suiteDir.resolve(caseName + ".java");
        }
        if (file == null || !Files.isRegularFile(file)) {
            return fallback;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            List<String> stepComments = new ArrayList<>();
            for (String l : lines) {
                String trimmed = l.trim();
                if (trimmed.startsWith("//") && !trimmed.startsWith("///")) {
                    String comment = trimmed.substring(2).trim();
                    if (!comment.isEmpty() && !comment.startsWith("import") && !comment.startsWith("package")) {
                        stepComments.add(comment);
                    }
                }
            }
            if (!stepComments.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int idx = 1;
                for (String c : stepComments) {
                    if (c.matches("^\\d+\\..*")) {
                        sb.append(c).append("\n");
                    } else {
                        sb.append(idx++).append(". ").append(c).append("\n");
                    }
                }
                return sb.toString().trim();
            }
        } catch (Exception ignored) {
        }
        return I18nService.get(lang, "export.tc.steps.fallback_nav");
    }

    private static void createSummarySheet(Sheet sheet, String scopeTitle, String scopeDescription,
            List<SuiteContainer> suites, List<TestCaseRowData> cases, Styles styles,
            ExportLanguage lang) {
        sheet.setDisplayGridlines(false);

        // Title Header
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(34);
        Cell titleA = titleRow.createCell(0);
        titleA.setCellValue(I18nService.get(lang, "export.tc.overview.title"));
        titleA.setCellStyle(styles.title);
        Cell titleB = titleRow.createCell(1);
        titleB.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        // Subtitle bar
        Row subRow = sheet.createRow(1);
        subRow.setHeightInPoints(22);
        Cell subA = subRow.createCell(0);
        subA.setCellValue(I18nService.get(lang, "export.tc.overview.subtitle_bar", scopeTitle));
        subA.setCellStyle(styles.subtitleBar);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

        int r = 3;
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.scope"), scopeTitle, styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.description"), scopeDescription, styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.export_date"),
                DISPLAY_DT.format(LocalDateTime.now()), styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.standard"),
                I18nService.get(lang, "export.tc.overview.standard_value"), styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.export_by"),
                I18nService.get(lang, "export.tc.overview.generated_by_value"), styles);

        r++; // blank row

        // Statistics
        Row statHeader = sheet.createRow(r++);
        statHeader.setHeightInPoints(22);
        Cell statHCell = statHeader.createCell(0);
        statHCell.setCellValue(I18nService.get(lang, "export.tc.overview.stats"));
        statHCell.setCellStyle(styles.section);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 3));

        int totalCases = cases.size();
        long readyCount = cases.stream().filter(c -> "Ready".equalsIgnoreCase(c.status)).count();
        long draftCount = cases.stream().filter(c -> "Draft".equalsIgnoreCase(c.status)).count();
        long totalSteps = cases.stream().mapToLong(c -> c.steps.size()).sum();

        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.total_suites"),
                String.valueOf(suites.size()), styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.total_cases"), String.valueOf(totalCases),
                styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.ready_cases"),
                String.format("%d (%.1f%%)", readyCount, totalCases > 0 ? (readyCount * 100.0 / totalCases) : 0),
                styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.draft_cases"),
                String.format("%d (%.1f%%)", draftCount, totalCases > 0 ? (draftCount * 100.0 / totalCases) : 0),
                styles);
        r = summaryPair(sheet, r, I18nService.get(lang, "export.tc.overview.total_steps"), String.valueOf(totalSteps),
                styles);

        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 45 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 20 * 256);
    }

    private static int summaryPair(Sheet sheet, int rowIdx, String label, String value, Styles styles) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(20);
        Cell c0 = row.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(styles.label);

        Cell c1 = row.createCell(1);
        c1.setCellValue(value);
        c1.setCellStyle(styles.value);

        return rowIdx + 1;
    }

    public static final int EXCEL_MAX_CELL_CHARS = 32700;

    public static String safeCellText(String text) {
        if (text == null)
            return "";
        if (text.length() > EXCEL_MAX_CELL_CHARS) {
            return text.substring(0, EXCEL_MAX_CELL_CHARS) + "\n... [TRUNCATED - EXCEEDED EXCEL 32,767 CHAR LIMIT]";
        }
        return text;
    }

    private static void createCasesSheet(Sheet sheet, List<TestCaseRowData> cases, Styles styles, ExportLanguage lang) {
        sheet.setDisplayGridlines(true);
        sheet.createFreezePane(0, 1);

        // Header
        Row header = sheet.createRow(0);
        header.setHeightInPoints(26);

        String[] headers = new String[] {
                I18nService.get(lang, "export.tc.table.num"),
                I18nService.get(lang, "export.tc.table.id"),
                I18nService.get(lang, "export.tc.table.name"),
                I18nService.get(lang, "export.tc.table.group"),
                I18nService.get(lang, "export.tc.table.suite"),
                I18nService.get(lang, "export.tc.table.priority"),
                I18nService.get(lang, "export.tc.table.status"),
                I18nService.get(lang, "export.tc.table.preconditions"),
                I18nService.get(lang, "export.tc.table.steps_summary"),
                I18nService.get(lang, "export.tc.table.test_data"),
                I18nService.get(lang, "export.tc.table.expected"),
                I18nService.get(lang, "export.tc.table.script_notes")
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.tableHeader);
        }

        // Rows
        int r = 1;
        for (TestCaseRowData c : cases) {
            Row row = sheet.createRow(r++);
            boolean zebra = (r % 2 == 0);

            // 0: STT
            Cell c0 = row.createCell(0);
            c0.setCellValue(c.index);
            c0.setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

            // 1: Mã Test Case
            Cell c1 = row.createCell(1);
            c1.setCellValue(safeCellText(c.id));
            c1.setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

            // 2: Tên Test Case
            Cell c2 = row.createCell(2);
            c2.setCellValue(safeCellText(c.name));
            c2.setCellStyle(zebra ? styles.dataOddBold : styles.dataEvenBold);

            // 3: Nhóm
            Cell c3 = row.createCell(3);
            c3.setCellValue(safeCellText(c.groupPath));
            c3.setCellStyle(zebra ? styles.dataOdd : styles.dataEven);

            // 4: Suite
            Cell c4 = row.createCell(4);
            c4.setCellValue(safeCellText(c.suiteName));
            c4.setCellStyle(zebra ? styles.dataOdd : styles.dataEven);

            // 5: Độ ưu tiên
            Cell c5 = row.createCell(5);
            c5.setCellValue(safeCellText(c.priority));
            org.apache.poi.ss.usermodel.CellStyle pStyle = styles.priorityMedium;
            if (c.priority != null && (c.priority.contains("P0") || c.priority.contains("P1"))) {
                pStyle = styles.priorityHigh;
            } else if (c.priority != null && c.priority.contains("P3")) {
                pStyle = styles.priorityLow;
            }
            c5.setCellStyle(pStyle);

            // 6: Trạng thái
            Cell c6 = row.createCell(6);
            c6.setCellValue(safeCellText(c.status));
            c6.setCellStyle("Ready".equalsIgnoreCase(c.status) ? styles.statusReady : styles.statusDraft);

            // 7: Điều kiện tiên quyết
            Cell c7 = row.createCell(7);
            c7.setCellValue(safeCellText(c.preconditions));
            c7.setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

            // 8: Các bước thực hiện
            Cell c8 = row.createCell(8);
            c8.setCellValue(safeCellText(c.stepsSummary));
            c8.setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

            // 9: Dữ liệu kiểm thử
            Cell c9 = row.createCell(9);
            c9.setCellValue(safeCellText(c.testDataSummary));
            c9.setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

            // 10: Kết quả mong đợi
            Cell c10 = row.createCell(10);
            c10.setCellValue(safeCellText(c.expectedResults));
            c10.setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

            // 11: File kịch bản
            Cell c11 = row.createCell(11);
            c11.setCellValue(safeCellText(c.scriptFile));
            c11.setCellStyle(zebra ? styles.dataOdd : styles.dataEven);
        }

        // Apply widths and auto-filter
        for (int i = 0; i < CASES_WIDTHS.length; i++) {
            sheet.setColumnWidth(i, CASES_WIDTHS[i] * 256);
        }
        if (cases.size() > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, cases.size(), 0, headers.length - 1));
        }
    }

    private static void createStepsSheet(Sheet sheet, List<TestCaseRowData> cases, Styles styles, ExportLanguage lang) {
        sheet.setDisplayGridlines(true);
        sheet.createFreezePane(0, 1);

        Row header = sheet.createRow(0);
        header.setHeightInPoints(26);

        String[] headers = new String[] {
                I18nService.get(lang, "export.tc.table.num"),
                I18nService.get(lang, "export.tc.table.id"),
                I18nService.get(lang, "export.tc.table.name"),
                I18nService.get(lang, "export.tc.steps.step_num"),
                I18nService.get(lang, "export.tc.steps.action"),
                I18nService.get(lang, "export.tc.steps.target"),
                I18nService.get(lang, "export.tc.steps.data"),
                I18nService.get(lang, "export.tc.steps.desc"),
                I18nService.get(lang, "export.tc.steps.expected")
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.tableHeader);
        }

        int r = 1;
        int stepGlobalIndex = 1;
        for (TestCaseRowData c : cases) {
            if (c.steps.isEmpty()) {
                // If case doesn't have broken-down steps, write 1 summary row
                Row row = sheet.createRow(r++);
                boolean zebra = (r % 2 == 0);

                row.createCell(0).setCellValue(stepGlobalIndex++);
                row.getCell(0).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(1).setCellValue(safeCellText(c.id));
                row.getCell(1).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(2).setCellValue(safeCellText(c.name));
                row.getCell(2).setCellStyle(zebra ? styles.dataOddBold : styles.dataEvenBold);

                row.createCell(3).setCellValue(1);
                row.getCell(3).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(4).setCellValue("EXECUTE_SCRIPT");
                row.getCell(4).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(5).setCellValue(safeCellText(c.scriptFile));
                row.getCell(5).setCellStyle(zebra ? styles.dataOdd : styles.dataEven);

                row.createCell(6).setCellValue(safeCellText(c.testDataSummary));
                row.getCell(6).setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

                row.createCell(7).setCellValue(safeCellText(c.stepsSummary));
                row.getCell(7).setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

                row.createCell(8).setCellValue(safeCellText(c.expectedResults));
                row.getCell(8).setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);
                continue;
            }

            int stepNum = 1;
            for (TestStep st : c.steps) {
                Row row = sheet.createRow(r++);
                boolean zebra = (r % 2 == 0);

                row.createCell(0).setCellValue(stepGlobalIndex++);
                row.getCell(0).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(1).setCellValue(safeCellText(c.id));
                row.getCell(1).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(2).setCellValue(safeCellText(c.name));
                row.getCell(2).setCellStyle(zebra ? styles.dataOddBold : styles.dataEvenBold);

                row.createCell(3).setCellValue(st.getStepNumber() > 0 ? st.getStepNumber() : stepNum++);
                row.getCell(3).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(4).setCellValue(safeCellText(st.getAction()));
                row.getCell(4).setCellStyle(zebra ? styles.dataCenterOdd : styles.dataCenterEven);

                row.createCell(5).setCellValue(safeCellText(st.getTarget()));
                row.getCell(5).setCellStyle(zebra ? styles.dataOdd : styles.dataEven);

                row.createCell(6).setCellValue(safeCellText(st.getTestData()));
                row.getCell(6).setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

                row.createCell(7).setCellValue(safeCellText(st.getDescription()));
                row.getCell(7).setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);

                row.createCell(8).setCellValue(safeCellText(st.getExpectedResult()));
                row.getCell(8).setCellStyle(zebra ? styles.dataOddWrap : styles.dataEvenWrap);
            }
        }

        for (int i = 0; i < STEPS_WIDTHS.length; i++) {
            sheet.setColumnWidth(i, STEPS_WIDTHS[i] * 256);
        }
        if (r > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, r - 1, 0, headers.length - 1));
        }
    }

    private static void tintSheet(Sheet sheet, IndexedColors color) {
        if (sheet instanceof XSSFSheet xssf) {
            xssf.setTabColor(new XSSFColor(color, new DefaultIndexedColorMap()));
        }
    }

    private static byte[] hex(String rgb) {
        return new byte[] {
                (byte) Integer.parseInt(rgb.substring(0, 2), 16),
                (byte) Integer.parseInt(rgb.substring(2, 4), 16),
                (byte) Integer.parseInt(rgb.substring(4, 6), 16)
        };
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitleBar;
        final CellStyle section;
        final CellStyle label;
        final CellStyle value;
        final CellStyle tableHeader;
        final CellStyle dataOdd;
        final CellStyle dataEven;
        final CellStyle dataOddBold;
        final CellStyle dataEvenBold;
        final CellStyle dataCenterOdd;
        final CellStyle dataCenterEven;
        final CellStyle dataOddWrap;
        final CellStyle dataEvenWrap;
        final CellStyle statusReady;
        final CellStyle statusDraft;
        final CellStyle priorityHigh;
        final CellStyle priorityMedium;
        final CellStyle priorityLow;

        private Styles(CellStyle title, CellStyle subtitleBar, CellStyle section, CellStyle label,
                CellStyle value, CellStyle tableHeader, CellStyle dataOdd, CellStyle dataEven,
                CellStyle dataOddBold, CellStyle dataEvenBold, CellStyle dataCenterOdd,
                CellStyle dataCenterEven, CellStyle dataOddWrap, CellStyle dataEvenWrap,
                CellStyle statusReady, CellStyle statusDraft, CellStyle priorityHigh,
                CellStyle priorityMedium, CellStyle priorityLow) {
            this.title = title;
            this.subtitleBar = subtitleBar;
            this.section = section;
            this.label = label;
            this.value = value;
            this.tableHeader = tableHeader;
            this.dataOdd = dataOdd;
            this.dataEven = dataEven;
            this.dataOddBold = dataOddBold;
            this.dataEvenBold = dataEvenBold;
            this.dataCenterOdd = dataCenterOdd;
            this.dataCenterEven = dataCenterEven;
            this.dataOddWrap = dataOddWrap;
            this.dataEvenWrap = dataEvenWrap;
            this.statusReady = statusReady;
            this.statusDraft = statusDraft;
            this.priorityHigh = priorityHigh;
            this.priorityMedium = priorityMedium;
            this.priorityLow = priorityLow;
        }

        static Styles create(Workbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setFontName("Calibri");
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.WHITE.getIndex());

            Font subtitleFont = wb.createFont();
            subtitleFont.setFontName("Calibri");
            subtitleFont.setFontHeightInPoints((short) 11);
            subtitleFont.setColor(IndexedColors.WHITE.getIndex());

            Font sectionFont = wb.createFont();
            sectionFont.setFontName("Calibri");
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);
            sectionFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            Font headerFont = wb.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font labelFont = wb.createFont();
            labelFont.setFontName("Calibri");
            labelFont.setBold(true);
            labelFont.setFontHeightInPoints((short) 11);

            Font bodyFont = wb.createFont();
            bodyFont.setFontName("Calibri");
            bodyFont.setFontHeightInPoints((short) 11);

            Font boldFont = wb.createFont();
            boldFont.setFontName("Calibri");
            boldFont.setBold(true);
            boldFont.setFontHeightInPoints((short) 11);

            Font readyFont = wb.createFont();
            readyFont.setFontName("Calibri");
            readyFont.setBold(true);
            readyFont.setFontHeightInPoints((short) 10);
            ((XSSFFont) readyFont).setColor(new XSSFColor(READY_FG, new DefaultIndexedColorMap()));

            Font draftFont = wb.createFont();
            draftFont.setFontName("Calibri");
            draftFont.setBold(true);
            draftFont.setFontHeightInPoints((short) 10);
            ((XSSFFont) draftFont).setColor(new XSSFColor(DRAFT_FG, new DefaultIndexedColorMap()));

            Font p2Font = wb.createFont();
            p2Font.setFontName("Calibri");
            p2Font.setBold(true);
            p2Font.setFontHeightInPoints((short) 10);
            ((XSSFFont) p2Font).setColor(new XSSFColor(P2_FG, new DefaultIndexedColorMap()));

            CellStyle title = solid(wb, NAVY);
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.LEFT);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            title.setIndention((short) 1);

            CellStyle subtitleBar = solid(wb, NAVY_SOFT);
            subtitleBar.setFont(subtitleFont);
            subtitleBar.setVerticalAlignment(VerticalAlignment.CENTER);
            subtitleBar.setIndention((short) 1);

            CellStyle section = wb.createCellStyle();
            section.setFont(sectionFont);
            section.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle label = solid(wb, LABEL_BG);
            label.setFont(labelFont);
            label.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(label);

            CellStyle value = wb.createCellStyle();
            value.setFont(bodyFont);
            value.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(value);

            CellStyle tableHeader = solid(wb, HEADER_BG);
            tableHeader.setFont(headerFont);
            tableHeader.setAlignment(HorizontalAlignment.CENTER);
            tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(tableHeader);

            CellStyle dataOdd = wb.createCellStyle();
            dataOdd.setFont(bodyFont);
            dataOdd.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataOdd);

            CellStyle dataEven = solid(wb, ZEBRA);
            dataEven.setFont(bodyFont);
            dataEven.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataEven);

            CellStyle dataOddBold = wb.createCellStyle();
            dataOddBold.setFont(boldFont);
            dataOddBold.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataOddBold);

            CellStyle dataEvenBold = solid(wb, ZEBRA);
            dataEvenBold.setFont(boldFont);
            dataEvenBold.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataEvenBold);

            CellStyle dataCenterOdd = wb.createCellStyle();
            dataCenterOdd.setFont(bodyFont);
            dataCenterOdd.setAlignment(HorizontalAlignment.CENTER);
            dataCenterOdd.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataCenterOdd);

            CellStyle dataCenterEven = solid(wb, ZEBRA);
            dataCenterEven.setFont(bodyFont);
            dataCenterEven.setAlignment(HorizontalAlignment.CENTER);
            dataCenterEven.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataCenterEven);

            CellStyle dataOddWrap = wb.createCellStyle();
            dataOddWrap.setFont(bodyFont);
            dataOddWrap.setWrapText(true);
            dataOddWrap.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataOddWrap);

            CellStyle dataEvenWrap = solid(wb, ZEBRA);
            dataEvenWrap.setFont(bodyFont);
            dataEvenWrap.setWrapText(true);
            dataEvenWrap.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(dataEvenWrap);

            CellStyle statusReady = solid(wb, READY_BG);
            statusReady.setFont(readyFont);
            statusReady.setAlignment(HorizontalAlignment.CENTER);
            statusReady.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(statusReady);

            CellStyle statusDraft = solid(wb, DRAFT_BG);
            statusDraft.setFont(draftFont);
            statusDraft.setAlignment(HorizontalAlignment.CENTER);
            statusDraft.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(statusDraft);

            Font p1Font = wb.createFont();
            p1Font.setFontName("Calibri");
            p1Font.setBold(true);
            p1Font.setFontHeightInPoints((short) 10);
            ((XSSFFont) p1Font).setColor(new XSSFColor(P1_FG, new DefaultIndexedColorMap()));

            Font p3Font = wb.createFont();
            p3Font.setFontName("Calibri");
            p3Font.setBold(true);
            p3Font.setFontHeightInPoints((short) 10);
            ((XSSFFont) p3Font).setColor(new XSSFColor(P3_FG, new DefaultIndexedColorMap()));

            CellStyle priorityHigh = solid(wb, P1_BG);
            priorityHigh.setFont(p1Font);
            priorityHigh.setAlignment(HorizontalAlignment.CENTER);
            priorityHigh.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(priorityHigh);

            CellStyle priorityMedium = solid(wb, P2_BG);
            priorityMedium.setFont(p2Font);
            priorityMedium.setAlignment(HorizontalAlignment.CENTER);
            priorityMedium.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(priorityMedium);

            CellStyle priorityLow = solid(wb, P3_BG);
            priorityLow.setFont(p3Font);
            priorityLow.setAlignment(HorizontalAlignment.CENTER);
            priorityLow.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(priorityLow);

            return new Styles(title, subtitleBar, section, label, value, tableHeader,
                    dataOdd, dataEven, dataOddBold, dataEvenBold, dataCenterOdd, dataCenterEven,
                    dataOddWrap, dataEvenWrap, statusReady, statusDraft, priorityHigh,
                    priorityMedium, priorityLow);
        }

        private static CellStyle solid(Workbook wb, byte[] rgb) {
            XSSFCellStyle cs = (XSSFCellStyle) wb.createCellStyle();
            cs.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return cs;
        }

        private static void applyBorders(CellStyle cs) {
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
            if (cs instanceof XSSFCellStyle xcs) {
                XSSFColor bc = new XSSFColor(BORDER_COLOR, new DefaultIndexedColorMap());
                xcs.setTopBorderColor(bc);
                xcs.setBottomBorderColor(bc);
                xcs.setLeftBorderColor(bc);
                xcs.setRightBorderColor(bc);
            }
        }
    }
}
