package com.studio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusDisplayIsAllTest {

    @Test
    void filterSentinel_isAll() {
        assertTrue(StatusDisplay.isAll(null));
        assertTrue(StatusDisplay.isAll(""));
        assertTrue(StatusDisplay.isAll(StatusDisplay.ALL_LABEL));
        assertTrue(StatusDisplay.isAll("Tất cả"));
    }

    @Test
    void runScopeCodes_areNotFilterAll() {
        assertFalse(StatusDisplay.isAll("ALL"));
        assertFalse(StatusDisplay.isAll("GROUP"));
        assertFalse(StatusDisplay.isAll("SUITE"));
        assertFalse(StatusDisplay.isAll("CASE"));
    }
}
