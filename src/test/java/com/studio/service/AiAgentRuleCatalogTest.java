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
}
