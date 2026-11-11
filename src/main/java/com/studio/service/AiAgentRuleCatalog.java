package com.studio.service;

import com.studio.model.AiAgentRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fixed, in-code catalogue of agent rules.
 *
 * The set is deliberately immutable: a deployment must not be able to redefine what an agent is
 * allowed to do, so no rule is read from settings or any external file. Adding a rule is a code
 * change and therefore a reviewable one.
 *
 * Every rule states what the agent must produce (obligations), what it must never do
 * (prohibitions) and what evidence it must return. The orchestrator turns prohibitions into a
 * boundary check, so an agent that reports doing prohibited work is flagged instead of trusted.
 */
public final class AiAgentRuleCatalog {

    public static final String SPEC_MINER = "spec-miner";
    public static final String TEST_ARCHITECT = "test-architect";
    public static final String REALITY_CHECKER = "reality-checker";
    public static final String LOOP_ENGINEER = "loop-engineer";
    public static final String COORDINATOR = "coordinator";

    private static final Map<String, AiAgentRule> RULES = buildRules();

    private AiAgentRuleCatalog() {
    }

    private static Map<String, AiAgentRule> buildRules() {
        Map<String, AiAgentRule> map = new LinkedHashMap<>();

        AiAgentRule specMiner = new AiAgentRule(SPEC_MINER, "SpecMiner", "Bóc tách yêu cầu");
        specMiner.getObligations().add("Đọc toàn bộ tài liệu được giao và liệt kê từng màn hình/chức năng");
        specMiner.getObligations().add("Với mỗi màn hình, nêu trường nhập, hành vi và điều kiện hợp lệ");
        specMiner.getObligations().add("Nêu rõ phần nào không xác định được từ tài liệu");
        specMiner.getProhibitions().add("sinh test case");
        specMiner.getProhibitions().add("sửa mã nguồn");
        specMiner.getRequiredEvidence().add("danh sách màn hình kèm trường và hành vi");
        map.put(SPEC_MINER, specMiner);

        AiAgentRule testArchitect = new AiAgentRule(TEST_ARCHITECT, "TestArchitect", "Thiết kế test case");
        testArchitect.getObligations().add("Sinh test case từ đặc tả màn hình đã được cung cấp");
        testArchitect.getObligations().add("Mỗi case có bước thực hiện, dữ liệu kiểm thử và ít nhất một assertion");
        testArchitect.getObligations().add("Ghi rõ case nào truy vết tới yêu cầu nào");
        testArchitect.getProhibitions().add("chạy test");
        testArchitect.getProhibitions().add("sửa mã nguồn");
        testArchitect.getProhibitions().add("thay đổi đặc tả");
        testArchitect.getRequiredEvidence().add("số case trên mỗi màn hình và danh sách assertion");
        map.put(TEST_ARCHITECT, testArchitect);

        AiAgentRule realityChecker = new AiAgentRule(REALITY_CHECKER, "RealityChecker", "Kiểm tra độc lập");
        realityChecker.getObligations().add("Chấm điểm đầu ra của agent khác theo tiêu chí nêu trong nhiệm vụ");
        realityChecker.getObligations().add("Phát hiện case thiếu bước, thiếu assertion hoặc assertion không kiểm tra gì");
        realityChecker.getObligations().add("Nêu finding kèm vị trí cụ thể; không sửa nội dung");
        realityChecker.getProhibitions().add("sửa đầu ra của agent khác");
        realityChecker.getProhibitions().add("sinh test case mới");
        realityChecker.getRequiredEvidence().add("danh sách finding kèm mức độ");
        map.put(REALITY_CHECKER, realityChecker);

        AiAgentRule loopEngineer = new AiAgentRule(LOOP_ENGINEER, "LoopEngineer", "Sửa theo finding");
        loopEngineer.getObligations().add("Chỉ sửa đúng những gì finding của RealityChecker chỉ ra");
        loopEngineer.getObligations().add("Nêu diff trước/sau cho mỗi chỗ sửa");
        loopEngineer.getObligations().add("Dừng và báo BLOCKED khi hết ngân sách vòng sửa mà finding còn tồn tại");
        loopEngineer.getProhibitions().add("thêm test case không có trong finding");
        loopEngineer.getProhibitions().add("bỏ qua finding");
        loopEngineer.getRequiredEvidence().add("số finding đã đóng và số còn lại");
        map.put(LOOP_ENGINEER, loopEngineer);

        AiAgentRule coordinator = new AiAgentRule(COORDINATOR, "Coordinator", "Điều phối tác nhân");
        coordinator.getObligations().add("Phân rã yêu cầu của người dùng thành nhiệm vụ cho từng agent");
        coordinator.getObligations().add("Chọn ruleId cho mỗi nhiệm vụ từ danh sách rule hợp lệ");
        coordinator.getObligations().add("Nêu nhiệm vụ nào cần chạy sau nhiệm vụ nào");
        coordinator.getProhibitions().add("tự sinh test case");
        coordinator.getProhibitions().add("tự phân tích tài liệu");
        coordinator.getProhibitions().add("tự sửa mã nguồn");
        coordinator.getProhibitions().add("bịa ruleId ngoài danh mục");
        coordinator.getRequiredEvidence().add("danh sách nhiệm vụ kèm ruleId và thứ tự thực hiện");
        map.put(COORDINATOR, coordinator);

        return Collections.unmodifiableMap(map);
    }

    /** All rules as independent copies, in declaration order. */
    public static List<AiAgentRule> all() {
        List<AiAgentRule> out = new ArrayList<>(RULES.size());
        for (AiAgentRule r : RULES.values()) {
            out.add(copy(r));
        }
        return out;
    }

    /** Rule ids a planner may legitimately delegate to (excludes the coordinator itself). */
    public static List<String> delegableRuleIds() {
        List<String> ids = new ArrayList<>(RULES.keySet());
        ids.remove(COORDINATOR);
        return ids;
    }

    public static boolean isDelegable(String ruleId) {
        return ruleId != null && !COORDINATOR.equals(ruleId) && RULES.containsKey(ruleId);
    }

    public static Optional<AiAgentRule> find(String ruleId) {
        return ruleId == null ? Optional.empty() : Optional.ofNullable(RULES.get(ruleId)).map(AiAgentRuleCatalog::copy);
    }

    /**
     * Defensive copy: the catalogue is the authority on what an agent may do, so a caller that
     * mutates a returned rule must not change the rule the next agent receives.
     */
    private static AiAgentRule copy(AiAgentRule src) {
        AiAgentRule c = new AiAgentRule(src.getId(), src.getName(), src.getRole());
        c.getObligations().addAll(src.getObligations());
        c.getProhibitions().addAll(src.getProhibitions());
        c.getRequiredEvidence().addAll(src.getRequiredEvidence());
        return c;
    }

    public static AiAgentRule require(String ruleId) {
        return find(ruleId).orElseThrow(
                () -> new IllegalArgumentException("Không có rule nào mang id: " + ruleId));
    }
}
