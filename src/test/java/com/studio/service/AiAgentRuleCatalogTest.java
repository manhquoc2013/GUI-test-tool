package com.studio.service;

import com.studio.model.AiAgentRule;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue is the authority on what an agent may do, so these tests pin the properties
 * that make that guarantee real: rules exist, ids are unique, the set cannot be mutated, and
 * the coordinator can never be delegated to.
 */
class AiAgentRuleCatalogTest {

    @Test
    void everyRuleHasIdNameRoleAndBoundary() {
        List<AiAgentRule> rules = AiAgentRuleCatalog.all();
        assertFalse(rules.isEmpty());
        for (AiAgentRule r : rules) {
            assertFalse(r.getId() == null || r.getId().isBlank(), "rule thiếu id");
            assertFalse(r.getName() == null || r.getName().isBlank(), "rule thiếu tên: " + r.getId());
            assertFalse(r.getObligations().isEmpty(), "rule phải có nghĩa vụ: " + r.getId());
            assertFalse(r.getProhibitions().isEmpty(), "rule phải có điều cấm: " + r.getId());
            assertFalse(r.getRequiredEvidence().isEmpty(), "rule phải nêu bằng chứng: " + r.getId());
        }
    }

    @Test
    void ruleIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (AiAgentRule r : AiAgentRuleCatalog.all()) {
            assertTrue(seen.add(r.getId()), "ruleId trùng: " + r.getId());
        }
    }

    @Test
    void catalogueCannotBeMutatedThroughAll() {
        AiAgentRuleCatalog.all().clear();
        assertFalse(AiAgentRuleCatalog.all().isEmpty(), "all() phải trả bản sao, không phải view nội bộ");
    }

    @Test
    void coordinatorIsNeverDelegable() {
        assertFalse(AiAgentRuleCatalog.isDelegable(AiAgentRuleCatalog.COORDINATOR));
        assertFalse(AiAgentRuleCatalog.delegableRuleIds().contains(AiAgentRuleCatalog.COORDINATOR));
    }

    @Test
    void unknownRuleIsRejected() {
        assertFalse(AiAgentRuleCatalog.isDelegable("khong-ton-tai"));
        assertTrue(AiAgentRuleCatalog.find("khong-ton-tai").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AiAgentRuleCatalog.require("khong-ton-tai"));
    }

    @Test
    void knownSpecialistIsDelegable() {
        assertTrue(AiAgentRuleCatalog.isDelegable(AiAgentRuleCatalog.SPEC_MINER));
        assertEquals("SpecMiner", AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER).getName());
    }

    /**
     * Each caller receives its own rule instance, so a planner or orchestrator that mutates a
     * rule in memory cannot change what a later agent is allowed to do.
     */
    @Test
    void returnedRulesAreIndependentInstances() {
        AiAgentRule first = AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT);
        AiAgentRuleCatalog.all().forEach(r -> r.getProhibitions().clear());
        AiAgentRule second = AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT);
        assertFalse(second.getProhibitions().isEmpty(), "rule trong catalog không được bị thay đổi từ ngoài");
        assertNotSame(first, second);
    }

    /**
     * The handoff lives on the rule, so a defensive copy that drops it would silently return the
     * pipeline to "every agent runs blind" — the exact bug this edge exists to prevent.
     */
    @Test
    void declaredDependenciesSurviveTheDefensiveCopy() {
        assertTrue(AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT)
                .getDependsOn().contains(AiAgentRuleCatalog.SPEC_MINER));
        assertTrue(AiAgentRuleCatalog.require(AiAgentRuleCatalog.REALITY_CHECKER)
                .getDependsOn().contains(AiAgentRuleCatalog.TEST_ARCHITECT));
        assertTrue(AiAgentRuleCatalog.require(AiAgentRuleCatalog.LOOP_ENGINEER)
                .getDependsOn().contains(AiAgentRuleCatalog.REALITY_CHECKER));
        assertTrue(AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER).getDependsOn().isEmpty(),
                "the entry agent must not wait for anything");
    }

    @Test
    void everyDependencyNamesARealRuleAndTheGraphIsAcyclic() {
        for (AiAgentRule rule : AiAgentRuleCatalog.all()) {
            for (String dependency : rule.getDependsOn()) {
                assertTrue(AiAgentRuleCatalog.find(dependency).isPresent(),
                        rule.getId() + " phụ thuộc rule không tồn tại: " + dependency);
                assertFalse(dependency.equals(rule.getId()), rule.getId() + " không thể phụ thuộc chính nó");
            }
        }
        // Ancestor sets must terminate; a cycle would make wave levels meaningless.
        for (String ruleId : AiAgentRuleCatalog.delegableRuleIds()) {
            Set<String> ancestors = AiSubAgentOrchestrator.ancestorRuleIds(AiAgentRuleCatalog.require(ruleId));
            assertFalse(ancestors.contains(ruleId), "chu trình phụ thuộc tại " + ruleId);
        }
    }
}
