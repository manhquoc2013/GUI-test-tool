package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.AiSkill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AiMultiAgentLoopAndNamingTest {

    @Test
    public void testNewSpecializedAgentSkillsAndDetection() {
        // Slash commands
        assertEquals(AiSkill.MULTI_AGENT_LOOP, AiSkill.fromSlashCommand("/loop Tạo test case đăng nhập"));
        assertEquals(AiSkill.MULTI_AGENT_LOOP, AiSkill.fromSlashCommand("/agent Kiểm thử thanh toán"));
        assertEquals(AiSkill.REALITY_CHECKER_REVIEW, AiSkill.fromSlashCommand("/review Rà soát bộ test case"));
        assertEquals(AiSkill.REALITY_CHECKER_REVIEW, AiSkill.fromSlashCommand("/audit Kiểm toán kịch bản"));
        assertEquals(AiSkill.API_CONTRACT_TESTER, AiSkill.fromSlashCommand("/api Kiểm thử endpoint POST /orders"));

        // Semantic auto-detection
        assertEquals(AiSkill.MULTI_AGENT_LOOP,
                AiSkill.detectSkill("Sử dụng đa tác nhân và loop engineer để sinh kịch bản kiểm thử", false, false));
        assertEquals(AiSkill.REALITY_CHECKER_REVIEW,
                AiSkill.detectSkill("Hãy thẩm định test case và rà soát chất lượng test cho phân hệ này", false, false));
        assertEquals(AiSkill.API_CONTRACT_TESTER,
                AiSkill.detectSkill("Viết kịch bản kiểm thử API cho REST API tạo đơn hàng với bearer token", false, false));
    }

    @Test
    public void testRealityCheckerAndLoopEngineerSelfHealing() {
        AiTestGeneratorService service = new AiTestGeneratorService();
        AiMultiAgentCoordinator coordinator = service.getMultiAgentCoordinator();

        // Simulate a low-quality raw GenerationResult with generic group/suite, generic case name,
        // missing steps, missing assertions, missing test data, and forbidden Thread.sleep
        AiGeneratedCase rawCase = new AiGeneratedCase();
        rawCase.setName("Case_1");
        rawCase.setPriority(null);
        rawCase.setDescription("Kiểm thử tạo đơn hàng thành công khi nhập đầy đủ thông tin giao hàng hợp lệ");
        rawCase.setStartUrl("https://shop.example.com/orders");
        rawCase.setScriptCode("page.navigate(\"https://shop.example.com/orders\");\nThread.sleep(3000);\npage.click(\"#submit\");");

        AiTestGeneratorService.GenerationResult result = new AiTestGeneratorService.GenerationResult(
                "Tóm tắt kiểm thử đơn hàng",
                new ArrayList<>(List.of(rawCase)),
                "{}"
        );
        result.setSuggestedGroup("AI-Tests"); // Generic! Should be healed
        result.setSuggestedSuite("AI-Generated-Suite"); // Generic! Should be healed

        List<String> progressLogs = new ArrayList<>();
        AiMultiAgentCoordinator.AgentReviewReport report = coordinator.reviewAndHealResult(
                result,
                "Tạo bộ test case cho chức năng quản lý đơn hàng và giao hàng tại https://shop.example.com/orders",
                List.of(),
                true,
                service,
                progressLogs::add
        );

        assertNotNull(report);
        assertEquals(4, report.getAgentStagesExecuted().size());
        assertTrue(report.getFinalQualityScore() < 100,
                "An incomplete deliverable must not be scored 100 (actual=" + report.getFinalQualityScore() + ")");

        // Verify Group and Suite names were healed to accurate functional names
        assertEquals("QuanLyDonHang", result.getSuggestedGroup());
        assertEquals("KiemThu_QuanLyDonHangVaGiaoHang", result.getSuggestedSuite());

        // Verify case was healed structurally…
        AiGeneratedCase healed = result.getCases().get(0);
        assertTrue(healed.getName().startsWith("TC01_"), "Case name should be normalized with TC01_ prefix: " + healed.getName());
        assertEquals("P1", healed.getPriority());
        assertFalse(healed.getScriptCode().contains("Thread.sleep("), "Loop Engineer must strip hard Thread.sleep()");

        // …but missing work is reported as a gap, never invented.
        assertTrue(healed.getTestSteps() == null || healed.getTestSteps().isEmpty(),
                "Loop Engineer must not synthesize test steps the model never wrote");
        assertTrue(healed.getAssertions() == null || healed.getAssertions().isEmpty(),
                "Loop Engineer must not synthesize assertions the model never wrote");
        assertTrue(healed.getTestDataSets() == null || healed.getTestDataSets().isEmpty(),
                "Loop Engineer must not synthesize test data the model never wrote");
        assertTrue(report.getGaps().stream().anyMatch(g -> g.contains("bước thực thi")),
                "Missing steps must be reported: " + report.getGaps());
        assertTrue(report.getGaps().stream().anyMatch(g -> g.contains("assertion")),
                "Missing assertions must be reported: " + report.getGaps());
        assertTrue(report.getGaps().stream().anyMatch(g -> g.contains("dữ liệu kiểm thử")),
                "Missing test data must be reported: " + report.getGaps());
        assertFalse(progressLogs.isEmpty());
    }

    @Test
    public void testLoopEngineerAutonomousRetryOnMissingCases() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        OpenAiClient mockClient = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                int step = callCount.incrementAndGet();
                if (step == 1) {
                    // First attempt returns unstructured text with 0 cases
                    return new Completion("Tôi đã phân tích yêu cầu chức năng giỏ hàng của bạn.", "stop");
                }
                // Second attempt (triggered by Loop Engineer self-correction) returns structured JSON
                return new Completion("""
                        ```json
                        {
                          "summary": "Bộ kịch bản kiểm thử chức năng Giỏ hàng & Thanh toán",
                          "suggestedGroup": "MuaSam_GioHang",
                          "suggestedSuite": "KiemThu_GioHangVaThanhToan",
                          "cases": [
                            {
                              "name": "TC01_GioHang_ThemSanPhamThanhCong",
                              "description": "Thêm sản phẩm vào giỏ hàng và kiểm tra tổng tiền",
                              "priority": "P0",
                              "startUrl": "",
                              "testSteps": [
                                {"stepNumber": 1, "action": "OPEN", "target": "Trang chi tiết sản phẩm", "testData": "SP001", "expectedResult": "Hiển thị thông tin sản phẩm"},
                                {"stepNumber": 2, "action": "CLICK", "target": "Nút Thêm vào giỏ", "testData": "Số lượng: 2", "expectedResult": "Hiển thị thông báo thêm thành công"},
                                {"stepNumber": 3, "action": "VERIFY", "target": "Biểu tượng Giỏ hàng", "testData": "", "expectedResult": "Số lượng trong giỏ cập nhật là 2"}
                              ],
                              "testDataSets": [
                                {"scenario": "Thêm số lượng hợp lệ", "parameters": {"quantity": "2"}, "expectedOutcome": "SUCCESS"}
                              ],
                              "assertions": [
                                {"type": "ELEMENT_HAS_TEXT", "locator": "#cart-count", "value": "2", "status": "ACTIVE"}
                              ]
                            }
                          ]
                        }
                        ```
                        """, "stop");
            }
        };

        // The generation path now calls completeChat directly (to read finish_reason); delegate so this
        // mock keeps exercising the same scripted replies.
        OpenAiClient delegating = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode)
                    throws java.io.IOException, InterruptedException {
                return mockClient.completeChat(config, messages, outputTokenOverride);
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(delegating, new ObjectMapper());
        AiConfig config = new AiConfig();
        config.setApiKey("sk-test-key");

        AiTestGeneratorService.GenerationResult result = service.generateTestCases(
                config,
                "Hãy tạo bộ test case chi tiết cho chức năng thêm sản phẩm vào giỏ hàng và kiểm tra tổng tiền thanh toán",
                List.of(),
                "Nhóm đích: (Tự động tạo khi lưu), Suite đích: (Tự động tạo khi lưu)",
                null,
                List.of(),
                null,
                AiSkill.MULTI_AGENT_LOOP,
                null,
                false
        );

        assertEquals(2, callCount.get(), "Loop Engineer should trigger exactly 1 retry when first response has 0 cases");
        assertEquals(1, result.getCases().size(),
                "Loop Engineer must keep the single model-written case instead of fabricating companion scenarios");
        assertEquals("MuaSam_GioHang", result.getSuggestedGroup());
        assertEquals("KiemThu_GioHangVaThanhToan", result.getSuggestedSuite());
        assertEquals("TC01_GioHang_ThemSanPhamThanhCong", result.getCases().get(0).getName());
        assertEquals("", result.getCases().get(0).getScriptCode(), "When generateScript=false, scriptCode must be empty");
        assertNotNull(result.getAgentReviewReport());
        assertTrue(result.getAgentReviewReport().getFinalQualityScore() < 100,
                "A single happy-path case cannot be scored as a complete suite");
    }

    @Test
    public void testFunctionalGroupAndSuiteNamingAcrossScenarios() {
        // 1. Explicit AI suggestions when non-generic are preserved
        assertEquals("QuanLyNhanSu",
                AiMultiAgentCoordinator.resolveFunctionalGroupName("Test chấm công", List.of(), List.of(), "QuanLyNhanSu"));
        assertEquals("KiemThu_BangChamCongThang",
                AiMultiAgentCoordinator.resolveFunctionalSuiteName("Test chấm công", null, List.of(), List.of(), "KiemThu_BangChamCongThang"));

        // 2. Generic AI suggestions ("AI-Tests", "AI-Generated-Suite") are replaced by accurate functional names
        assertEquals("XacThuc_TaiKhoan",
                AiMultiAgentCoordinator.resolveFunctionalGroupName("Kiểm thử đăng nhập hệ thống với OTP", List.of(), List.of(), "AI-Tests"));
        assertEquals("KiemThu_DangNhapHeThong",
                AiMultiAgentCoordinator.resolveFunctionalSuiteName("Kiểm thử đăng nhập hệ thống với OTP", "Cuộc trò chuyện mới", List.of(), List.of(), "AI-Generated-Suite"));

        // 3. Extracted from Test Case names (TC01_<Feature>_<Scenario>)
        AiGeneratedCase c1 = new AiGeneratedCase();
        c1.setName("TC01_QuanLyKhoHang_NhapKho");
        AiGeneratedCase c2 = new AiGeneratedCase();
        c2.setName("TC02_QuanLyKhoHang_XuatKho");
        assertEquals("QuanLySanPham",
                AiMultiAgentCoordinator.resolveFunctionalGroupName("Tạo kịch bản kho hàng", List.of(), List.of(c1, c2), null));
        assertEquals("KiemThu_QuanLyKhoHang",
                AiMultiAgentCoordinator.resolveFunctionalSuiteName("Tạo kịch bản kho hàng", null, List.of(), List.of(c1, c2), null));

        // 4. Extracted from Vietnamese functional phrase ("chức năng ...")
        assertEquals("ChucNang_ChamCongNhanVien",
                AiMultiAgentCoordinator.resolveFunctionalGroupName("Viết bộ test case cho chức năng chấm công nhân viên theo ca", List.of(), List.of(), null));
        assertEquals("KiemThu_ChamCongNhanVien",
                AiMultiAgentCoordinator.resolveFunctionalSuiteName("Viết bộ test case cho chức năng chấm công nhân viên theo ca", null, List.of(), List.of(), null));
    }
}
