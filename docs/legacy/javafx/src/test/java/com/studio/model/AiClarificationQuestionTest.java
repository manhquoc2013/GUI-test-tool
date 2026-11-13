package com.studio.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiClarificationQuestionTest {

    @Test
    void testDefaultOptionSelection() {
        AiClarificationQuestion q = new AiClarificationQuestion("q1", "Question 1", List.of("Opt A", "Opt B"));
        assertEquals("Opt A", q.getDefaultOption());

        q.setSelectedAnswer("Opt B");
        assertEquals("Opt B", q.getDefaultOption());

        AiClarificationQuestion empty = new AiClarificationQuestion("q2", "Question 2", List.of());
        assertEquals("", empty.getDefaultOption());
    }

    @Test
    void testConstructorsAndProperties() {
        AiClarificationQuestion q = new AiClarificationQuestion("q_test", "Prompt?", List.of("Yes", "No"), true, false);
        assertEquals("q_test", q.getId());
        assertEquals("Prompt?", q.getQuestion());
        assertEquals(2, q.getOptions().size());
        assertTrue(q.isMultiSelect());
        assertFalse(q.isAllowCustomInput());

        q.setId("q_new");
        q.setQuestion("New Question");
        q.setOptions(List.of("1", "2", "3"));
        q.setMultiSelect(false);
        q.setAllowCustomInput(true);

        assertEquals("q_new", q.getId());
        assertEquals("New Question", q.getQuestion());
        assertEquals(3, q.getOptions().size());
        assertFalse(q.isMultiSelect());
        assertTrue(q.isAllowCustomInput());
    }

    @Test
    void testEqualsAndHashCode() {
        AiClarificationQuestion q1 = new AiClarificationQuestion("id1", "Q1", List.of());
        AiClarificationQuestion q2 = new AiClarificationQuestion("id1", "Q2", List.of());
        AiClarificationQuestion q3 = new AiClarificationQuestion("id2", "Q1", List.of());

        assertEquals(q1, q2);
        assertNotEquals(q1, q3);
        assertEquals(q1.hashCode(), q2.hashCode());
    }
}
