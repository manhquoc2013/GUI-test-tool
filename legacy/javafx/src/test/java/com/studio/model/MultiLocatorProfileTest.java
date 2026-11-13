package com.studio.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiLocatorProfileTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCandidateOrderingAndDeduplication() {
        MultiLocatorProfile profile = new MultiLocatorProfile("button#submit", List.of(
                "[data-testid='submit-btn']",
                "button:has-text('Submit')",
                "button#submit" // duplicate of primary
        ));

        List<String> candidates = profile.getOrderedCandidates();
        assertEquals(3, candidates.size());
        assertEquals("button#submit", candidates.get(0));
        assertEquals("[data-testid='submit-btn']", candidates.get(1));
        assertEquals("button:has-text('Submit')", candidates.get(2));
    }

    @Test
    void testPromoteCandidate() {
        MultiLocatorProfile profile = new MultiLocatorProfile("button#submit", List.of(
                "[data-testid='submit-btn']",
                "button:has-text('Submit')"
        ));

        MultiLocatorProfile promoted = profile.promote("button:has-text('Submit')");
        assertEquals("button:has-text('Submit')", promoted.getPrimary());
        assertTrue(promoted.getFallbacks().contains("button#submit"));
        assertTrue(promoted.getFallbacks().contains("[data-testid='submit-btn']"));
        assertEquals(3, promoted.getOrderedCandidates().size());
    }

    @Test
    void testJsonSerializationRoundtrip() throws Exception {
        MultiLocatorProfile profile = new MultiLocatorProfile("#btn-save", List.of(".btn-primary", "text='Save'"));
        String json = mapper.writeValueAsString(profile);
        MultiLocatorProfile deserialized = mapper.readValue(json, MultiLocatorProfile.class);

        assertEquals(profile.getPrimary(), deserialized.getPrimary());
        assertEquals(profile.getFallbacks(), deserialized.getFallbacks());
    }
}

