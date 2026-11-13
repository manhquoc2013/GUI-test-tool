package com.studio.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPriorityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testNormalizeVariousInputs() {
        assertEquals(TestPriority.P0, TestPriority.normalize("p0"));
        assertEquals(TestPriority.P0, TestPriority.normalize("CRITICAL"));
        assertEquals(TestPriority.P0, TestPriority.normalize("smoke"));
        assertEquals(TestPriority.P0, TestPriority.normalize("blocker"));

        assertEquals(TestPriority.P1, TestPriority.normalize("P1"));
        assertEquals(TestPriority.P1, TestPriority.normalize("high"));
        assertEquals(TestPriority.P1, TestPriority.normalize("core"));

        assertEquals(TestPriority.P2, TestPriority.normalize("P2"));
        assertEquals(TestPriority.P2, TestPriority.normalize("medium"));
        assertEquals(TestPriority.P2, TestPriority.normalize("regression"));

        assertEquals(TestPriority.P3, TestPriority.normalize("P3"));
        assertEquals(TestPriority.P3, TestPriority.normalize("low"));
        assertEquals(TestPriority.P3, TestPriority.normalize("edge"));

        assertEquals(TestPriority.P1, TestPriority.normalize(null));
        assertEquals(TestPriority.P1, TestPriority.normalize(""));
        assertEquals(TestPriority.P1, TestPriority.normalize("unknown"));
    }

    @Test
    void testGetLabelAndBadgeColor() {
        assertEquals("P0 - Critical / Smoke", TestPriority.getLabel("P0"));
        assertEquals("P1 - High / Core Flow", TestPriority.getLabel("P1"));
        assertEquals("P2 - Medium / Regression", TestPriority.getLabel("P2"));
        assertEquals("P3 - Low / Edge Case", TestPriority.getLabel("P3"));

        assertNotNull(TestPriority.getBadgeColor("P0"));
        assertNotNull(TestPriority.getBadgeColor("P1"));
        assertNotNull(TestPriority.getBadgeColor("P2"));
        assertNotNull(TestPriority.getBadgeColor("P3"));
    }

    @Test
    void testTestCaseAndAiGeneratedCasePrioritySerialization() throws Exception {
        TestCase tc = new TestCase("tc1", "Auth Case", "Desc", "AuthCase.java", "Ready");
        tc.setPriority("P0");
        assertEquals(TestPriority.P0, tc.getPriority());

        String json = mapper.writeValueAsString(tc);
        TestCase deserialized = mapper.readValue(json, TestCase.class);
        assertEquals(TestPriority.P0, deserialized.getPriority());

        AiGeneratedCase genCase = new AiGeneratedCase();
        genCase.setName("CartCase");
        genCase.setPriority("P1");
        assertEquals(TestPriority.P1, genCase.getPriority());

        String genJson = mapper.writeValueAsString(genCase);
        AiGeneratedCase desGen = mapper.readValue(genJson, AiGeneratedCase.class);
        assertEquals(TestPriority.P1, desGen.getPriority());
    }
}

