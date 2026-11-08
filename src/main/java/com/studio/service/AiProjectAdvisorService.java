package com.studio.service;

import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestGroup;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import com.studio.util.StatusDisplay;
import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service providing conversational test intelligence:
 * 1. Searching test cases across the project.
 * 2. Evaluating and grading existing test case scripts against Agency-Agents best practices.
 * 3. Analyzing suite coverage gaps to recommend missing test scenarios.
 */
public class AiProjectAdvisorService {

    private final ProjectStore projectStore;

    public AiProjectAdvisorService() {
        this(new ProjectStore());
    }

    public AiProjectAdvisorService(ProjectStore projectStore) {
        this.projectStore = projectStore != null ? projectStore : new ProjectStore();
    }

    public record CaseMatch(
            String caseName,
            String suiteName,
            String groupName,
            String status,
            String startUrl,
            String matchReason,
            Path suiteDir
    ) {}

    public record SearchResult(
            String query,
            List<CaseMatch> matches,
            String markdownReport
    ) {}

    public record EvaluationResult(
            String caseName,
            String suiteName,
            int score,
            String grade,
            List<String> strengths,
            List<String> warnings,
            List<String> criticalFlaws,
            String recommendedFix,
            String markdownReport
    ) {}

    public record GapAnalysisResult(
            String suiteName,
            int totalCases,
            List<String> coveredScenarios,
            List<String> missingGaps,
            String markdownReport
    ) {}

    // ── 1. Search Test Cases ──

    public SearchResult searchCases(String query, Path projectDir) {
        Path root = projectDir != null ? projectDir : StudioPaths.getProjectDir();
        List<CaseMatch> matches = new ArrayList<>();
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        try {
            TestGroup rootGroup = projectStore.loadTree();
            searchInGroup(rootGroup, normalizedQuery, matches, root);
        } catch (Exception e) {
            AiLoggingService.error("AI-ADVISOR", "Lỗi tìm kiếm test cases", e);
        }

        StringBuilder md = new StringBuilder();
        md.append("## 🔍 KẾT QUẢ TÌM KIẾM TEST CASES\n\n");
        if (query != null && !query.isBlank()) {
            md.append("- **Từ khóa tìm kiếm**: `").append(query).append("`\n");
        }
        md.append("- **Số kết quả tìm thấy**: ").append(matches.size()).append(" test case(s)\n\n");

        if (matches.isEmpty()) {
            md.append("⚠️ Không tìm thấy test case nào khớp với từ khóa trong dự án.\n\n");
            md.append("*Gợi ý*: Thử tìm kiếm theo tên ngắn hơn, hoặc kiểm tra xem Suite/Case đã được tạo chưa.");
        } else {
            md.append("| Test Case | Suite | Nhóm | Trạng thái | URL / Bắt đầu | Điểm khớp |\n");
            md.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
            for (CaseMatch m : matches) {
                String stLabel = StatusDisplay.label(m.status());
                String url = m.startUrl() != null && !m.startUrl().isBlank() ? m.startUrl() : "—";
                if (url.length() > 35) {
                    url = url.substring(0, 32) + "...";
                }
                md.append("| **").append(m.caseName()).append("** | ")
                        .append(m.suiteName()).append(" | ")
                        .append(m.groupName()).append(" | ")
                        .append(stLabel).append(" | ")
                        .append(url).append(" | ")
                        .append(m.matchReason()).append(" |\n");
            }
            md.append("\n💡 *Bạn có thể yêu cầu tôi đánh giá chất lượng của bất kỳ case nào ở trên bằng cách chat: `đánh giá test case <tên case>`*");
        }

        return new SearchResult(query, matches, md.toString());
    }

    private void searchInGroup(TestGroup group, String query, List<CaseMatch> matches, Path currentPath) {
        if (group == null) return;
        String gName = group.getName() != null ? group.getName() : "Root";
        Path groupDir = currentPath.resolve(gName);

        if (group.getSuites() != null) {
            for (TestSuite suite : group.getSuites()) {
                Path suiteDir = groupDir.resolve(suite.getName());
                if (suite.getCases() != null) {
                    for (TestCase tc : suite.getCases()) {
                        String reason = matchCase(tc, suite.getName(), gName, query, suiteDir);
                        if (reason != null) {
                            matches.add(new CaseMatch(
                                    tc.getName(),
                                    suite.getName(),
                                    gName,
                                    tc.getStatus(),
                                    tc.getStartUrl(),
                                    reason,
                                    suiteDir
                            ));
                        }
                    }
                }
            }
        }

        if (group.getChildren() != null) {
            for (TestGroup child : group.getChildren()) {
                searchInGroup(child, query, matches, groupDir);
            }
        }
    }

    private String matchCase(TestCase tc, String suiteName, String groupName, String query, Path suiteDir) {
        if (query.isEmpty()) return "Tất cả";
        String name = tc.getName() != null ? tc.getName().toLowerCase(Locale.ROOT) : "";
        if (name.contains(query)) return "Tên case khớp";

        String desc = tc.getDescription() != null ? tc.getDescription().toLowerCase(Locale.ROOT) : "";
        if (desc.contains(query)) return "Mô tả khớp";

        String sName = suiteName.toLowerCase(Locale.ROOT);
        if (sName.contains(query)) return "Tên Suite khớp";

        String url = tc.getStartUrl() != null ? tc.getStartUrl().toLowerCase(Locale.ROOT) : "";
        if (url.contains(query)) return "URL xuất phát khớp";

        // Check inside script code if exists
        try {
            Path scriptFile = suiteDir.resolve(tc.getName() + ".java");
            if (Files.exists(scriptFile)) {
                String code = Files.readString(scriptFile).toLowerCase(Locale.ROOT);
                if (code.contains(query)) {
                    return "Mã kịch bản Java khớp";
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ── 2. Evaluate & Audit Test Case ──

    public EvaluationResult evaluateCase(String targetCaseName, Path suiteDir) {
        TestCase foundCase = null;
        String foundSuiteName = suiteDir != null ? suiteDir.getFileName().toString() : "Suite";
        Path targetSuiteDir = suiteDir;
        String scriptCode = "";

        // If suiteDir provided, look up case in it
        if (targetSuiteDir != null && Files.exists(targetSuiteDir)) {
            try {
                TestSuite suite = projectStore.loadAndMigrateSuite(targetSuiteDir);
                foundSuiteName = suite.getName();
                if (suite.getCases() != null) {
                    for (TestCase tc : suite.getCases()) {
                        if (tc.getName().equalsIgnoreCase(targetCaseName.trim())
                                || (tc.getId() != null && tc.getId().equalsIgnoreCase(targetCaseName.trim()))) {
                            foundCase = tc;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // If not found yet, search across all project suites
        if (foundCase == null) {
            try {
                TestGroup rootGroup = projectStore.loadTree();
                var searchHit = findCaseGlobally(rootGroup, targetCaseName.trim(), StudioPaths.getProjectDir());
                if (searchHit != null) {
                    foundCase = searchHit.tc;
                    foundSuiteName = searchHit.suiteName;
                    targetSuiteDir = searchHit.suiteDir;
                }
            } catch (Exception ignored) {}
        }

        if (foundCase == null) {
            String errorMd = "## ⚠️ KHÔNG TÌM THẤY TEST CASE\n\nKhông tìm thấy test case có tên: `\"" + targetCaseName + "\"` trong dự án.";
            return new EvaluationResult(targetCaseName, foundSuiteName, 0, "N/A", List.of(), List.of(), List.of(errorMd), "", errorMd);
        }

        // Read script code
        if (targetSuiteDir != null) {
            try {
                Path javaFile = targetSuiteDir.resolve(foundCase.getName() + ".java");
                if (Files.exists(javaFile)) {
                    scriptCode = Files.readString(javaFile);
                }
            } catch (Exception ignored) {}
        }

        return runAudit(foundCase, foundSuiteName, scriptCode);
    }

    private static class CaseHit {
        TestCase tc;
        String suiteName;
        Path suiteDir;
        CaseHit(TestCase tc, String suiteName, Path suiteDir) {
            this.tc = tc;
            this.suiteName = suiteName;
            this.suiteDir = suiteDir;
        }
    }

    private CaseHit findCaseGlobally(TestGroup group, String query, Path curPath) {
        if (group == null) return null;
        Path gDir = curPath.resolve(group.getName() != null ? group.getName() : "Root");
        if (group.getSuites() != null) {
            for (TestSuite s : group.getSuites()) {
                Path sDir = gDir.resolve(s.getName());
                if (s.getCases() != null) {
                    for (TestCase tc : s.getCases()) {
                        if (tc.getName().equalsIgnoreCase(query) || tc.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                            return new CaseHit(tc, s.getName(), sDir);
                        }
                    }
                }
            }
        }
        if (group.getChildren() != null) {
            for (TestGroup c : group.getChildren()) {
                var hit = findCaseGlobally(c, query, gDir);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private EvaluationResult runAudit(TestCase tc, String suiteName, String code) {
        int score = 100;
        List<String> strengths = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> criticalFlaws = new ArrayList<>();
        StringBuilder fixBuilder = new StringBuilder();

        // Check 1: Hard sleep check (Agency-Agents Rule #1)
        if (Pattern.compile("Thread\\.sleep|waitForTimeout").matcher(code).find()) {
            score -= 30;
            criticalFlaws.add("Phát hiện `Thread.sleep` hoặc `page.waitForTimeout(...)`. Đây là nguyên nhân hàng đầu gây flaky test! Hãy thay bằng `waitForLoadState` hoặc auto-waiting locators.");
        } else {
            strengths.add("Không sử dụng hard sleep (`Thread.sleep` / `waitForTimeout`). Tuân thủ chuẩn Deterministic Playwright.");
        }

        // Check 2: Assertions count & depth
        int assertCount = (tc.getAssertions() != null) ? tc.getAssertions().size() : 0;
        long codeAssertCount = countMatches(code, "assertThat\\(");
        if (assertCount == 0 && codeAssertCount == 0) {
            score -= 30;
            criticalFlaws.add("Test case hoàn toàn KHÔNG CÓ assertion nào! Test case không có kiểm tra kết quả thì không có giá trị bảo vệ hồi quy.");
        } else if (assertCount == 1 && codeAssertCount <= 1) {
            score -= 10;
            warnings.add("Chỉ có 1 assertion duy nhất. Khuyến nghị bổ sung đa chiều: kiểm tra cả URL/State lẫn phần tử hiển thị.");
        } else {
            strengths.add("Có " + Math.max(assertCount, (int) codeAssertCount) + " assertions kiểm thử kết quả đa điểm.");
        }

        // Check 3: Locator resilience (Agency-Agents Rule #3)
        if (code.contains("nth-child") || code.contains("div > div > div")) {
            score -= 15;
            warnings.add("Bộ chọn DOM mong manh (`nth-child` / đường dẫn tag sâu). Khi UI thay đổi nhẹ, kịch bản sẽ gãy. Hãy thay bằng `getByRole`, `getByLabel` hoặc `data-testid`.");
        } else if (code.contains("getByRole") || code.contains("getByLabel") || code.contains("getByTestId") || code.contains("healLocator")) {
            strengths.add("Sử dụng bộ chọn người dùng bền vững (`getByRole` / `getByLabel` / `StudioRuntime.healLocator`).");
        }

        // Check 4: Strict Mode disambiguation (Agency-Agents standard)
        if (code.contains("has-text(") && !code.contains(".first()") && !code.contains("data-testid")) {
            score -= 10;
            warnings.add("Bộ chọn `has-text(...)` có thể khớp nhiều phần tử cùng lúc dẫn đến lỗi Playwright Strict Mode Violation. Khuyến nghị bổ sung `.first()` hoặc gán selector phạm vi cụ thể.");
        }

        // Check 5: Steps definition
        int stepCount = (tc.getTestSteps() != null) ? tc.getTestSteps().size() : 0;
        if (stepCount > 0) {
            strengths.add("Đầy đủ " + stepCount + " bước kịch bản NLP declarative (Arrange-Act-Assert rõ ràng).");
        } else {
            warnings.add("Chưa có danh sách NLP test steps; chỉ có mã nguồn Java thuần.");
        }

        // Grade calculation
        score = Math.max(0, Math.min(100, score));
        String grade;
        if (score >= 85) grade = "A (Xuất sắc)";
        else if (score >= 70) grade = "B (Tốt)";
        else if (score >= 50) grade = "C (Cần cải thiện)";
        else grade = "D (Nguy cơ Flaky cao / Thiếu kiểm thử)";

        // Build Markdown Report
        StringBuilder md = new StringBuilder();
        md.append("## 📊 KẾT QUẢ ĐÁNH GIÁ CHẤT LƯỢNG TEST CASE\n\n");
        md.append("- **Test Case**: `").append(tc.getName()).append("`\n");
        md.append("- **Suite**: ").append(suiteName).append("\n");
        md.append("- **Trạng thái**: ").append(StatusDisplay.label(tc.getStatus())).append("\n");
        md.append("- **Điểm chất lượng**: **").append(score).append("/100** — Hạng: **").append(grade).append("**\n\n");

        md.append("### ✅ ĐIỂM MẠNH (STRENGTHS)\n");
        if (strengths.isEmpty()) {
            md.append("- Chưa ghi nhận điểm mạnh nổi bật.\n");
        } else {
            for (String s : strengths) {
                md.append("- ✅ ").append(s).append("\n");
            }
        }
        md.append("\n");

        if (!criticalFlaws.isEmpty()) {
            md.append("### 🚨 LỖI NGHIÊM TRỌNG (CRITICAL FLAWS)\n");
            for (String f : criticalFlaws) {
                md.append("- ❌ ").append(f).append("\n");
            }
            md.append("\n");
        }

        if (!warnings.isEmpty()) {
            md.append("### ⚠️ CẢNH BÁO CẦN TỐI ƯU (WARNINGS)\n");
            for (String w : warnings) {
                md.append("- ⚠️ ").append(w).append("\n");
            }
            md.append("\n");
        }

        md.append("### 💡 ĐỀ XUẤT CẢI THIỆN (ACTIONABLE RECOMMENDATION)\n");
        if (score >= 85) {
            md.append("Kịch bản đạt chuẩn cao theo tiêu chuẩn Agency-Agents! Có thể tự tin chạy trong CI/CD pipeline.\n");
        } else {
            md.append("Khuyến nghị tái cấu trúc theo tiêu chuẩn Playwright E2E:\n");
            md.append("1. Loại bỏ các lệnh chờ cố định (`Thread.sleep`), thay thế bằng `assertThat(locator).isVisible()`.\n");
            md.append("2. Sử dụng `page.getByRole(...)` hoặc `StudioRuntime.healLocator(...)` để tăng độ bền khi UI đổi thiết kế.\n");
            md.append("3. Bổ sung ít nhất 2 assertions kiểm tra kết quả mong đợi (URL chuyển hướng + thông báo giao diện).\n");
        }

        return new EvaluationResult(tc.getName(), suiteName, score, grade, strengths, warnings, criticalFlaws, fixBuilder.toString(), md.toString());
    }

    private static long countMatches(String text, String sub) {
        if (text == null || sub == null || sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // ── 3. Suite Coverage Gap Analysis ──

    public GapAnalysisResult analyzeSuiteGaps(Path suiteDir) {
        if (suiteDir == null || !Files.exists(suiteDir)) {
            String err = "⚠️ Thư mục Suite không tồn tại.";
            return new GapAnalysisResult("Unknown", 0, List.of(), List.of(), err);
        }

        TestSuite suite;
        try {
            suite = projectStore.loadAndMigrateSuite(suiteDir);
        } catch (IOException e) {
            String err = "❌ Không đọc được Suite: " + e.getMessage();
            return new GapAnalysisResult(suiteDir.getFileName().toString(), 0, List.of(), List.of(), err);
        }

        String suiteName = suite.getName() != null ? suite.getName() : suiteDir.getFileName().toString();
        List<TestCase> cases = suite.getCases() != null ? suite.getCases() : List.of();
        List<String> covered = new ArrayList<>();
        List<String> missingGaps = new ArrayList<>();

        boolean hasPositive = false;
        boolean hasNegative = false;
        boolean hasBoundary = false;
        boolean hasAuth = false;

        for (TestCase tc : cases) {
            String n = tc.getName().toLowerCase(Locale.ROOT);
            String d = tc.getDescription() != null ? tc.getDescription().toLowerCase(Locale.ROOT) : "";
            if (n.contains("valid") || n.contains("success") || n.contains("happy") || d.contains("thành công") || d.contains("hợp lệ")) {
                hasPositive = true;
                covered.add("Luồng chính (Happy Path): `" + tc.getName() + "`");
            }
            if (n.contains("invalid") || n.contains("fail") || n.contains("negative") || d.contains("thất bại") || d.contains("không hợp lệ")) {
                hasNegative = true;
                covered.add("Luồng xác thực lỗi (Negative Path): `" + tc.getName() + "`");
            }
            if (n.contains("boundary") || n.contains("bva") || n.contains("empty") || n.contains("max") || d.contains("biên") || d.contains("rỗng")) {
                hasBoundary = true;
                covered.add("Kiểm thử dữ liệu biên (Boundary Value): `" + tc.getName() + "`");
            }
            if (n.contains("login") || n.contains("auth") || d.contains("đăng nhập") || d.contains("xác thực")) {
                hasAuth = true;
            }
        }

        if (!hasPositive) {
            missingGaps.add("Thiếu kịch bản kiểm thử luồng chính thành công (Happy Path) với dữ liệu chuẩn.");
        }
        if (!hasNegative) {
            missingGaps.add("Thiếu kiểm thử xác thực dữ liệu không hợp lệ (Negative Validation): để trống trường bắt buộc, sai định dạng email/số, hoặc mật khẩu yếu.");
        }
        if (!hasBoundary) {
            missingGaps.add("Thiếu kiểm thử giá trị biên (Boundary Value Analysis): nhập độ dài tối thiểu, độ dài tối đa (1000+ ký tự), hoặc ký tự đặc biệt XSS/Unicode.");
        }
        if (hasAuth && !hasNegative) {
            missingGaps.add("Thiếu kiểm thử bảo mật đăng nhập: nhập sai mật khẩu 5 lần, tài khoản bị khóa, hoặc phiên đăng nhập hết hạn (Session timeout).");
        }
        if (cases.size() <= 2) {
            missingGaps.add("Số lượng test case trong suite còn ít (" + cases.size() + " case). Cần bao phủ thêm các nhánh điều kiện thay thế.");
        }

        StringBuilder md = new StringBuilder();
        md.append("## 🧩 PHÂN TÍCH ĐỘ BAO PHỦ & KHOẢNG TRỐNG KIỂM THỬ (GAP ANALYSIS)\n\n");
        md.append("- **Suite**: `").append(suiteName).append("`\n");
        md.append("- **Tổng số case hiện có**: ").append(cases.size()).append(" case(s)\n");
        md.append("- **Trạng thái Suite**: ").append(StatusDisplay.label(suite.getStatus())).append("\n\n");

        md.append("### 📋 CÁC PHẠM VI ĐÃ ĐƯỢC BAO PHỦ:\n");
        if (covered.isEmpty()) {
            md.append("- Chưa có test case phân loại cụ thể.\n");
        } else {
            for (String c : covered) {
                md.append("- ✔️ ").append(c).append("\n");
            }
        }
        md.append("\n");

        md.append("### 🚨 KHOẢNG TRỐNG KIỂM THỬ CẦN BỔ SUNG (GAPS IDENTIFIED):\n");
        if (missingGaps.isEmpty()) {
            md.append("🎉 Suite đã bao phủ đầy đủ cả Happy Path, Negative Path và Boundary Cases!\n");
        } else {
            for (String g : missingGaps) {
                md.append("- ⚠️ **Cần bổ sung**: ").append(g).append("\n");
            }
            md.append("\n💡 *Bạn có thể bấm sinh test case mới hoặc chat: `sinh test case cho các khoảng trống trên` để tôi tự động bổ sung kịch bản Playwright hoàn chỉnh!*");
        }

        return new GapAnalysisResult(suiteName, cases.size(), covered, missingGaps, md.toString());
    }
}
