package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.AiSkill;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AiMultiAgentLargeSrsMultiScreenTest {

        /**
         * Simulates a 60-page SRS Markdown document containing 10 functional screens
         * with PDF page-marker headers ("### Trang N"), tables, fields, and validation
         * rules.
         */
        private String generate60PageSrsMarkdown() {
                StringBuilder sb = new StringBuilder();
                sb.append("""
                                ---
                                source_file: "HeThong_SRS_ChiTiet_v2.0.pdf"
                                source_type: "pdf"
                                units_count: 60
                                ---

                                # TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS) - HỆ THỐNG THƯƠNG MẠI ĐIỆN TỬ
                                ### Trang 1
                                ## 1. Giới thiệu tổng quan & Mục đích tài liệu
                                Tài liệu này đặc tả chi tiết 10 màn hình chức năng của hệ thống.

                                ### Trang 2
                                ## 2. Phạm vi & Thuật ngữ
                                Bao gồm người dùng vãng lai, khách hàng, nhân viên và quản trị viên.
                                """);

                String[] screenNames = {
                                "Đăng nhập & Xác thực 2FA",
                                "Đăng ký Tài khoản & Kích hoạt Email",
                                "Quên mật khẩu & Khôi phục OTP",
                                "Danh mục Sản phẩm & Bộ lọc tìm kiếm",
                                "Chi tiết Sản phẩm & Đánh giá Review",
                                "Giỏ hàng & Áp dụng Voucher Khuyến mãi",
                                "Thanh toán & Cổng thanh toán Trực tuyến",
                                "Theo dõi Đơn hàng & Quản lý Vận chuyển",
                                "Hồ sơ Người dùng & Sổ địa chỉ",
                                "Báo cáo Doanh thu & Quản trị Hệ thống"
                };

                for (int i = 0; i < screenNames.length; i++) {
                        int pageStart = 3 + (i * 5);
                        sb.append("\n### Trang ").append(pageStart).append("\n");
                        sb.append("## Màn hình ").append(i + 1).append(": ").append(screenNames[i]).append("\n");
                        sb.append("Mô tả: Đặc tả chi tiết các trường giao diện và quy tắc nghiệp vụ cho ")
                                        .append(screenNames[i]).append(".\n\n");
                        sb.append("| STT | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation |\n");
                        sb.append("|---|---|---|---|---|\n");
                        sb.append("| 1 | Field_").append(i + 1).append(
                                        "_A | String | Có | Độ dài từ 5 đến 50 ký tự, không chứa ký tự đặc biệt |\n");
                        sb.append("| 2 | Field_").append(i + 1)
                                        .append("_B | Number | Có | Giá trị nguyên dương từ 1 đến 9999 |\n");
                        sb.append("| 3 | Field_").append(i + 1)
                                        .append("_C | Email/Text | Không | Định dạng chuẩn RFC hoặc bỏ trống |\n\n");
                        sb.append("### Trang ").append(pageStart + 1).append("\n");
                        sb.append("Quy tắc nghiệp vụ trọng tâm:\n");
                        sb.append("- Ràng buộc: Bắt buộc nhập đầy đủ Field_").append(i + 1).append("_A và Field_")
                                        .append(i + 1).append("_B.\n");
                        sb.append("- Thông báo lỗi: Hiển thị lỗi khi vi phạm quy tắc validation hoặc trùng lặp dữ liệu.\n");
                        sb.append("- Giá trị biên: Kiểm tra tại Min=1, Max=9999 và Max+1=10000.\n\n");
                }
                return sb.toString();
        }

        @Test
        public void testSpecMinerExtracts10ScreensFrom60PageSrs() {
                String srsMarkdown = generate60PageSrsMarkdown();
                DocumentParserService.ParsedDocument srsDoc = new DocumentParserService.ParsedDocument(
                                "HeThong_SRS_ChiTiet_v2.0.pdf",
                                Path.of("/docs/HeThong_SRS_ChiTiet_v2.0.pdf"),
                                false,
                                srsMarkdown,
                                "text/markdown",
                                srsMarkdown.length());

                List<AiMultiAgentCoordinator.ScreenSpecSection> screens = AiMultiAgentCoordinator.extractScreenSections(
                                "Tạo test scenario cho 10 màn hình từ tài liệu SRS", List.of(srsDoc));

                assertNotNull(screens);
                assertEquals(10, screens.size(),
                                "Agent 1 (Spec Miner) must extract all 10 screens accurately from the 60-page SRS");

                for (int i = 0; i < 10; i++) {
                        AiMultiAgentCoordinator.ScreenSpecSection s = screens.get(i);
                        assertEquals(i + 1, s.screenIndex());
                        assertNotNull(s.screenTitle());
                        assertFalse(s.featureToken().isBlank());
                        assertFalse(s.extractedFields().isEmpty(), "Screen " + (i + 1) + " must have extracted fields");
                        assertFalse(s.extractedRules().isEmpty(), "Screen " + (i + 1) + " must have extracted rules");
                }
        }

        @Test
        public void testAmbiguityAnalyzerTriggersClarificationForLargeSrs() {
                String srsMarkdown = generate60PageSrsMarkdown();
                DocumentParserService.ParsedDocument srsDoc = new DocumentParserService.ParsedDocument(
                                "SRS_Commerce.pdf",
                                Path.of("/docs/SRS_Commerce.pdf"),
                                false,
                                srsMarkdown,
                                "text/markdown",
                                srsMarkdown.length());

                AiAmbiguityAnalyzer analyzer = new AiAmbiguityAnalyzer();
                AiAmbiguityAnalyzer.AnalysisResult analysis = analyzer.analyze(
                                "Tạo test scenario cho 10 màn hình",
                                true,
                                false,
                                List.of(srsDoc));

                assertNotNull(analysis);
                assertTrue(analysis.needsClarification(),
                                "Ambiguity Analyzer MUST trigger clarification for large 10-screen SRS");
                assertFalse(analysis.questions().isEmpty());
                assertTrue(analysis.initialClarificationMessage().contains("10 màn hình"));
        }

        @Test
        public void testPerScreenCoverageExpansionProduces30To50CasesEvenIfLlmReturns1CasePerScreen() {
                AiTestGeneratorService service = new AiTestGeneratorService();
                AiMultiAgentCoordinator coordinator = service.getMultiAgentCoordinator();

                String srsMarkdown = generate60PageSrsMarkdown();
                DocumentParserService.ParsedDocument srsDoc = new DocumentParserService.ParsedDocument(
                                "SRS_Commerce.pdf",
                                Path.of("/docs/SRS_Commerce.pdf"),
                                false,
                                srsMarkdown,
                                "text/markdown",
                                srsMarkdown.length());

                // Simulate an LLM that only produced 1 test case per screen (10 test cases
                // total)
                List<AiGeneratedCase> tenSingleCases = new ArrayList<>();
                List<AiMultiAgentCoordinator.ScreenSpecSection> screens = AiMultiAgentCoordinator
                                .extractScreenSections("Tạo test scenario cho 10 màn hình", List.of(srsDoc));
                assertEquals(10, screens.size());

                for (AiMultiAgentCoordinator.ScreenSpecSection screen : screens) {
                        AiGeneratedCase single = new AiGeneratedCase();
                        single.setName("TC01_" + screen.featureToken() + "_ThanhCong");
                        single.setDescription("Kiểm thử thành công trên " + screen.screenTitle());
                        single.setPriority("P0");
                        tenSingleCases.add(single);
                }
                assertEquals(10, tenSingleCases.size());

                AiTestGeneratorService.GenerationResult result = new AiTestGeneratorService.GenerationResult(
                                "Tổng hợp 10 cases ban đầu",
                                tenSingleCases,
                                "");

                List<String> progressLogs = new ArrayList<>();
                AiMultiAgentCoordinator.AgentReviewReport report = coordinator.reviewAndHealResult(
                                result,
                                "Tạo test scenario cho 10 màn hình từ tài liệu SRS đính kèm",
                                List.of(srsDoc),
                                false,
                                service,
                                progressLogs::add);

                assertNotNull(report);
                List<AiGeneratedCase> finalCases = result.getCases();
                // 10 screens * 4 cases/screen = 40 test cases
                assertTrue(finalCases.size() >= 35 && finalCases.size() <= 50,
                                "Agent 4 (Loop Engineer) must expand 10 single-cases into 30 - 50 cases (actual: "
                                                + finalCases.size() + ")");

                // Check sequential numbering and per-screen distribution
                assertEquals("TC01_", finalCases.get(0).getName().substring(0, 5));
                assertEquals(String.format("TC%02d_", finalCases.size()),
                                finalCases.get(finalCases.size() - 1).getName().substring(0, 5));

                // Verify quality score is 100
                assertEquals(100, report.getFinalQualityScore());
        }

        @Test
        public void testMultiBatchOrchestrationExecutesBatchesSequentially() throws Exception {
                AtomicInteger batchInvocations = new AtomicInteger(0);

                OpenAiClient mockBatchClient = new OpenAiClient() {
                        @Override
                        public String chatCompletion(AiConfig config, List<ChatMessage> messages) {
                                int call = batchInvocations.incrementAndGet();
                                // Return 8 cases per batch of 2 screens
                                return """
                                                ```json
                                                {
                                                  "summary": "Đã thiết kế các test case cho lô màn hình",
                                                  "suggestedGroup": "ThuongMaiDienTu",
                                                  "suggestedSuite": "KiemThu_ToanDien10ManHinh",
                                                  "cases": [
                                                    {"name": "TC01_ManHinh_HappyPath", "description": "Happy path", "priority": "P0", "testSteps": [{"stepNumber": 1, "action": "NAVIGATE", "target": "Page", "testData": "", "expectedResult": "OK"}, {"stepNumber": 2, "action": "FILL", "target": "Field", "testData": "abc", "expectedResult": "OK"}, {"stepNumber": 3, "action": "CLICK", "target": "Btn", "testData": "", "expectedResult": "OK"}], "testDataSets": [{"scenario": "Valid", "parameters": {}, "expectedOutcome": "OK"}], "assertions": [{"type": "ELEMENT_VISIBLE", "locator": "body", "value": "", "status": "ACTIVE"}]},
                                                    {"name": "TC02_ManHinh_Negative", "description": "Negative", "priority": "P1", "testSteps": [{"stepNumber": 1, "action": "NAVIGATE", "target": "Page", "testData": "", "expectedResult": "OK"}, {"stepNumber": 2, "action": "FILL", "target": "Field", "testData": "", "expectedResult": "Error"}, {"stepNumber": 3, "action": "CLICK", "target": "Btn", "testData": "", "expectedResult": "Error"}], "testDataSets": [{"scenario": "Invalid", "parameters": {}, "expectedOutcome": "FAIL"}], "assertions": [{"type": "PAGE_HAS_TEXT", "locator": "body", "value": "Lỗi", "status": "ACTIVE"}]}
                                                  ]
                                                }
                                                ```
                                                """;
                        }
                };

                AiTestGeneratorService service = new AiTestGeneratorService(mockBatchClient, new ObjectMapper());
                AiConfig config = new AiConfig();
                config.setApiKey("sk-mock-test-key");

                String srsMarkdown = generate60PageSrsMarkdown();
                DocumentParserService.ParsedDocument srsDoc = new DocumentParserService.ParsedDocument(
                                "SRS_Commerce.pdf",
                                Path.of("/docs/SRS_Commerce.pdf"),
                                false,
                                srsMarkdown,
                                "text/markdown",
                                srsMarkdown.length());

                List<String> progressMsgs = new ArrayList<>();
                AiTestGeneratorService.GenerationResult result = service.generateTestCases(
                                config,
                                "[PHẠM VI] Bao quát đầy đủ 10 màn hình [TIỀN ĐIỀU KIỆN] Sẵn sàng [XÁC MINH] Toàn diện",
                                List.of(srsDoc),
                                "",
                                null,
                                List.of(),
                                progressMsgs::add,
                                AiSkill.MULTI_AGENT_LOOP,
                                null,
                                false);

                assertNotNull(result);
                assertEquals(5, batchInvocations.get(),
                                "10 screens with batchSize=2 must trigger 5 focused LLM batch invocations");
                assertTrue(result.getCases().size() >= 35,
                                "Multi-batch orchestration + Loop Engineer expansion must produce 35+ test cases (actual: "
                                                + result.getCases().size() + ")");
        }

        @Test
        void testScenarioOnlyPrompt_GeneratesScenariosWithoutAutomationScript() throws Exception {
                OpenAiClient mockClient = new OpenAiClient() {
                        @Override
                        public String chatCompletion(AiConfig config, List<ChatMessage> messages) {
                                return """
                                                ```json
                                                {
                                                  "summary": "Đã thiết kế 3 kịch bản kiểm thử chuẩn QA/ISTQB",
                                                  "suggestedGroup": "XacThuc_TaiKhoan",
                                                  "suggestedSuite": "KiemThu_DangNhap",
                                                  "cases": [
                                                    {
                                                      "name": "SC01_DangNhap_ThanhCong",
                                                      "description": "Kịch bản đăng nhập tài khoản chuẩn",
                                                      "priority": "P0",
                                                      "preconditions": ["Tài khoản đã được kích hoạt"],
                                                      "testSteps": [
                                                        {"stepNumber": 1, "action": "NAVIGATE", "target": "LoginPage", "testData": "", "description": "Mở trang đăng nhập", "expectedResult": "Trang mở thành công"},
                                                        {"stepNumber": 2, "action": "FILL", "target": "EmailField", "testData": "user@test.com", "description": "Nhập email", "expectedResult": "Email hợp lệ"},
                                                        {"stepNumber": 3, "action": "CLICK", "target": "LoginBtn", "testData": "", "description": "Bấm đăng nhập", "expectedResult": "Chuyển trang Dashboard"}
                                                      ],
                                                      "testDataSets": [{"scenario": "Valid credentials", "parameters": {}, "expectedOutcome": "SUCCESS"}],
                                                      "assertions": [{"type": "PAGE_HAS_TEXT", "locator": "h1", "value": "Dashboard", "status": "ACTIVE"}],
                                                      "scriptCode": ""
                                                    }
                                                  ]
                                                }
                                                ```
                                                """;
                        }
                };

                AiTestGeneratorService service = new AiTestGeneratorService(mockClient, new ObjectMapper());
                AiConfig config = new AiConfig();
                config.setApiKey("sk-mock-key");

                AiTestGeneratorService.GenerationResult result = service.generateTestCases(
                                config,
                                "Tạo test scenario cho chức năng đăng nhập [phạm vi] Đầy đủ [tiền điều kiện] Chuẩn bị sẵn [xác minh] Toàn diện",
                                List.of(),
                                "https://example.com/login",
                                null,
                                List.of(),
                                null,
                                AiSkill.PLAYWRIGHT_E2E,
                                null,
                                null); // generateScript=null -> auto detected based on scenario intent

                assertNotNull(result);
                assertFalse(result.getCases().isEmpty());
                for (AiGeneratedCase c : result.getCases()) {
                        assertEquals("", c.getScriptCode(),
                                        "When user requests test scenario only, scriptCode MUST be empty");
                        assertFalse(c.getTestSteps().isEmpty(), "Scenario must contain detailed test steps");
                        assertFalse(c.getAssertions().isEmpty(), "Scenario must contain assertions");
                }
        }

        @Test
        void testTestCaseAndScriptPrompt_EnablesAutomationScript() throws Exception {
                OpenAiClient mockClient = new OpenAiClient() {
                        @Override
                        public String chatCompletion(AiConfig config, List<ChatMessage> messages) {
                                return """
                                                ```json
                                                {
                                                  "summary": "Đã sinh test case và test script runnable",
                                                  "suggestedGroup": "XacThuc_TaiKhoan",
                                                  "suggestedSuite": "KiemThu_DangNhap",
                                                  "cases": [
                                                    {
                                                      "name": "TC01_DangNhap_ThanhCong",
                                                      "description": "Test case đăng nhập chuẩn",
                                                      "priority": "P0",
                                                      "preconditions": ["Tài khoản sẵn sàng"],
                                                      "testSteps": [{"stepNumber": 1, "action": "NAVIGATE", "target": "LoginPage", "testData": "", "expectedResult": "OK"}],
                                                      "testDataSets": [{"scenario": "Valid", "parameters": {}, "expectedOutcome": "OK"}],
                                                      "assertions": [{"type": "PAGE_HAS_TEXT", "locator": "h1", "value": "Dashboard", "status": "ACTIVE"}],
                                                      "scriptCode": "page.navigate(\\"https://example.com/login\\");"
                                                    }
                                                  ]
                                                }
                                                ```
                                                """;
                        }
                };

                AiTestGeneratorService service = new AiTestGeneratorService(mockClient, new ObjectMapper());
                AiConfig config = new AiConfig();
                config.setApiKey("sk-mock-key");

                AiTestGeneratorService.GenerationResult result = service.generateTestCases(
                                config,
                                "Tạo test case và test script tự động hóa theo test scenario [phạm vi] Đầy đủ [tiền điều kiện] Sẵn sàng [xác minh] Toàn diện",
                                List.of(),
                                "",
                                null,
                                List.of(),
                                null,
                                AiSkill.PLAYWRIGHT_E2E,
                                null,
                                null);

                assertNotNull(result);
                assertFalse(result.getCases().isEmpty());
                assertTrue(result.getCases().get(0).getScriptCode().contains("page.navigate(\"https://example.com/login\");"),
                                "Script code must contain runnable Playwright automation command");
        }
}
