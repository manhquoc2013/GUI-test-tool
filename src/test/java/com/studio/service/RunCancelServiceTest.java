package com.studio.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RunCancelServiceTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RunCancelService.resetForTests();
    }

    @Test
    void beginRunActivatesAndClearsCancel() {
        RunCancelService.requestCancel();
        assertFalse(RunCancelService.isRunActive());

        RunCancelService.beginRun();
        assertTrue(RunCancelService.isRunActive());
        assertFalse(RunCancelService.isCancelRequested());
    }

    @Test
    void requestCancelSetsFlagWhileActive() {
        RunCancelService.beginRun();
        RunCancelService.requestCancel();
        assertTrue(RunCancelService.isCancelRequested());
        assertTrue(RunCancelService.isRunActive());

        RunCancelService.endRun();
        assertFalse(RunCancelService.isRunActive());
        assertFalse(RunCancelService.isCancelRequested());
    }

    @Test
    void requestCancelIgnoredWhenIdle() {
        RunCancelService.requestCancel();
        assertFalse(RunCancelService.isCancelRequested());
    }

    @Test
    void listenerNotifiedOnBeginAndEnd() {
        AtomicInteger ticks = new AtomicInteger();
        Runnable listener = ticks::incrementAndGet;
        RunCancelService.addListener(listener);
        try {
            RunCancelService.beginRun();
            RunCancelService.endRun();
            assertTrue(ticks.get() >= 2);
        } finally {
            RunCancelService.removeListener(listener);
        }
    }
}
