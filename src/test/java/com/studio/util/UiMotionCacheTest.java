package com.studio.util;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the motion-cache lifecycle: a page animated with a translated/faded huge scene graph renders
 * into a texture for the duration of the motion, and returns to native sharpness afterwards (or when
 * an interrupted motion is cleared). Leaving {@code cache=true} behind would freeze live content.
 */
class UiMotionCacheTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized by another test class.
        }
        Platform.setImplicitExit(false);
    }

    private void runFx(Runnable r) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void aPageUsesABitmapCacheOnlyWhileItMoves() throws Exception {
        runFx(() -> {
            StackPane page = new StackPane();
            UiMotion.playPageEnter(page);
            assertTrue(page.isCache(), "A moving page renders into a texture");
            assertTrue(UiMotion.isBusy(page), "The page reports an in-flight motion");

            UiMotion.clearBusy(page);
            assertFalse(page.isCache(),
                    "An interrupted motion must not leave the page cached");
            assertFalse(UiMotion.isBusy(page));
        });
    }

    @Test
    void clearingAnIdleNodeIsHarmless() throws Exception {
        runFx(() -> {
            StackPane node = new StackPane();
            UiMotion.clearBusy(node);
            UiMotion.clearBusy(null);
            assertFalse(node.isCache());
        });
    }
}
