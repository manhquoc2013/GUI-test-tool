package com.studio.service;

import com.studio.model.AiGeneratedCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Regression guard for the coverage analyzer: tokenising a case description must tolerate repeated
 * words. Model output routinely repeats a term ("xác định ... xác định"), and {@code Set.of(...)}
 * throws {@code IllegalArgumentException: duplicate element: ...} on the second occurrence, which
 * aborted a whole generation turn.
 */
class AiCoverageAnalyzerTest {

    @Test
    void detectKindToleratesRepeatedWords() {
        // "định" normalises to the token "inh" (the đ becomes a separator), so this name repeats it.
        AiGeneratedCase c = new AiGeneratedCase(
                "Đăng nhập thành công khi xác định lại định dạng",
                "Người dùng xác định tài khoản rồi xác định mật khẩu",
                "");

        assertDoesNotThrow(() -> AiCoverageAnalyzer.detectKind(c),
                "Repeated words must not abort coverage detection");
        assertEquals("HappyPath", AiCoverageAnalyzer.detectKind(c));
    }

    @Test
    void analyzeToleratesRepeatedWordsAcrossCases() {
        AiGeneratedCase c = new AiGeneratedCase(
                "Xác định trạng thái khi xác định lại",
                "Màn hình hiển thị màn hình xác nhận",
                "");
        c.setTestId("TC01_LOGIN_ThanhCong");
        c.setScreenId("LOGIN");
        c.setRequirementRef("SRS-1.1");
        AiMultiAgentCoordinator.ScreenSpecSection screen = new AiMultiAgentCoordinator.ScreenSpecSection(
                1, "Đăng nhập", "LOGIN", "", List.of(), List.of(), "");

        assertDoesNotThrow(() -> AiCoverageAnalyzer.analyze(List.of(c), List.of(screen)),
                "Repeated words must not abort the whole coverage report");
    }
}
