package com.studio.util;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the eased scrolling contract: the viewport approaches its target monotonically, never
 * overshoots it, and a programmatic move is not a single teleport.
 */
class SmoothScrollTest {

    private static final double CONTENT_HEIGHT = 5_000;
    private static final double VIEWPORT_HEIGHT = 400;

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
    void anEasingStepMovesTowardsTheTargetWithoutOvershooting() {
        double step = SmoothScroll.nextValue(0.0, 0.8, 1.0 / 60);
        assertTrue(step > 0, "The viewport must move towards a larger target");
        assertTrue(step < 0.8, "One frame must not teleport to the target");

        double backwards = SmoothScroll.nextValue(1.0, 0.2, 1.0 / 60);
        assertTrue(backwards < 1.0, "The viewport must move towards a smaller target");
        assertTrue(backwards > 0.2, "One frame must not teleport backwards");
    }

    @Test
    void repeatedStepsConvergeMonotonically() {
        double value = 0;
        double previous = 0;
        for (int frame = 0; frame < 600; frame++) {
            value = SmoothScroll.nextValue(value, 1.0, 1.0 / 60);
            assertTrue(value >= previous, "The value must never move backwards");
            assertTrue(value <= 1.0, "The value must never overshoot the target");
            previous = value;
        }
        assertEquals(1.0, value, 1e-6, "The easing must settle on the target");
    }

    @Test
    void aZeroElapsedFrameDoesNotMove() {
        assertEquals(0.4, SmoothScroll.nextValue(0.4, 1.0, 0), 1e-9);
        assertEquals(0.4, SmoothScroll.nextValue(0.4, 0.0, 0), 1e-9);
    }

    @Test
    void installingTwiceReusesTheSameController() throws Exception {
        runFx(() -> {
            ScrollPane pane = tallPane();
            SmoothScroll first = SmoothScroll.install(pane);
            SmoothScroll second = SmoothScroll.install(pane);
            assertNotNull(first);
            assertSame(first, second, "A pane must never get two competing animations");
        });
    }

    @Test
    void jumpToPlacesTheViewportImmediately() throws Exception {
        runFx(() -> {
            ScrollPane pane = tallPane();
            SmoothScroll smoothScroll = SmoothScroll.install(pane);
            smoothScroll.jumpTo(0.42);
            assertEquals(0.42, pane.getVvalue(), 1e-9,
                    "A rebuild needs an immediate position, not an animation");
            assertFalse(smoothScroll.isTargetingBottom(), "0.42 is not the bottom");
        });
    }

    @Test
    void aClampedJumpDoesNotTeleportTheNextWheelGesture() throws Exception {
        runFx(() -> {
            ScrollPane pane = tallPane();
            SmoothScroll smoothScroll = SmoothScroll.install(pane);
            // A rebuild asks for the bottom before the skin has measured the content; the skin then
            // clamps it back. The next notch must ease from the real position, never from the stale one.
            smoothScroll.jumpTo(1.0);
            pane.setVvalue(0.5);
            fireWheel(pane, 120);
            assertTrue(smoothScroll.targetValue() < 0.5,
                    "The gesture must start where the viewport is, not where the discarded jump pointed");
            assertTrue(smoothScroll.targetValue() > 0.3,
                    "One notch may not jump a whole page");
        });
    }

    @Test
    void aWheelGestureOverANestedControlIsLeftToIt() throws Exception {
        runFx(() -> {
            ScrollPane nested = new ScrollPane(new Region());
            nested.setPrefSize(300, 200);
            VBox content = new VBox(nested);
            content.setPrefHeight(CONTENT_HEIGHT);
            content.setMinHeight(CONTENT_HEIGHT);
            ScrollPane pane = new ScrollPane(content);
            pane.setFitToWidth(true);
            new Scene(pane, 800, VIEWPORT_HEIGHT);
            pane.applyCss();
            pane.layout();

            SmoothScroll smoothScroll = SmoothScroll.install(pane);
            smoothScroll.jumpTo(0.5);
            fireWheel(nested, 120);
            assertEquals(0.5, smoothScroll.targetValue(), 1e-9,
                    "A list or pane inside the content scrolls itself; easing the outer pane too would move both");
        });
    }

    private static void fireWheel(ScrollPane pane, double deltaY) {
        ScrollEvent event = new ScrollEvent(ScrollEvent.SCROLL, 100, 100, 100, 100,
                false, false, false, false, false, false,
                0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
        Event.fireEvent(pane, event);
    }

    private static ScrollPane tallPane() {
        Region content = new Region();
        content.setPrefHeight(CONTENT_HEIGHT);
        content.setMinHeight(CONTENT_HEIGHT);
        StackPane wrapper = new StackPane(content);
        wrapper.setPrefHeight(CONTENT_HEIGHT);

        ScrollPane pane = new ScrollPane(wrapper);
        pane.setFitToWidth(true);
        new Scene(pane, 800, VIEWPORT_HEIGHT);
        pane.applyCss();
        pane.layout();
        return pane;
    }
}
