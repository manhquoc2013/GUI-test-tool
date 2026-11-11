package com.studio.util;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * Wheel scrolling for a {@link ScrollPane} that moves the viewport instead of teleporting it.
 *
 * <p>The default JavaFX wheel handler rewrites {@code vvalue} in one jump per notch. On a long
 * transcript that is both visually abrupt and expensive: every notch becomes a full repaint at a new
 * offset. This helper keeps a target {@code vvalue} and eases the live value towards it on an
 * {@link AnimationTimer}, so a notch reads as one continuous movement and the expensive content is
 * repainted along a short, smooth path.</p>
 *
 * <p>Events are handled in the bubbling phase: a nested virtualized control (a table inside a
 * drawer) scrolls first and consumes its own wheel events, and only what reaches the outer pane is
 * eased here.</p>
 */
public final class SmoothScroll {

    /** How fast the live value chases the target: 1 - e^(-rate * seconds). */
    private static final double EASING_RATE_PER_SECOND = 24.0;
    /** Below this the remaining distance is invisible, so the timer stops and releases the CPU. */
    private static final double SETTLE_EPSILON = 0.0002;
    /** Guard against a runaway animation when a trackpad emits an unbounded delta. */
    private static final double MAX_WHEEL_DELTA = 6_000.0;
    /** A new wheel event may not push the target further than this per event. */
    private static final double MAX_TARGET_STEP = 0.35;

    private final ScrollPane scrollPane;
    private final AnimationTimer timer;
    private double target;
    private double lastFrameNanos;
    private boolean animating;

    private SmoothScroll(ScrollPane scrollPane) {
        this.scrollPane = scrollPane;
        this.target = scrollPane.getVvalue();
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now);
            }
        };
    }

    /** Installs wheel easing on {@code scrollPane}; repeated calls are ignored. */
    public static SmoothScroll install(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return null;
        }
        Object installed = scrollPane.getProperties().get(SmoothScroll.class.getName());
        if (installed instanceof SmoothScroll existing) {
            return existing;
        }
        SmoothScroll smoothScroll = new SmoothScroll(scrollPane);
        smoothScroll.wire();
        scrollPane.getProperties().put(SmoothScroll.class.getName(), smoothScroll);
        return smoothScroll;
    }

    private void wire() {
        target = scrollPane.getVvalue();
        scrollPane.addEventHandler(ScrollEvent.SCROLL, this::onScroll);
        // A thumb drag or a scrollbar click owns the value directly; fighting it would oscillate.
        scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> syncTargetToCurrent());
    }

    private void onScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) {
            return;
        }
        double range = scrollablePixels();
        if (range <= 0) {
            return;
        }
        double deltaPixels = clamp(-event.getDeltaY(), -MAX_WHEEL_DELTA, MAX_WHEEL_DELTA);
        double step = clamp(deltaPixels / range, -MAX_TARGET_STEP, MAX_TARGET_STEP);
        double next = clamp(target + step, 0, 1);
        if (next == target) {
            return;
        }
        target = next;
        startAnimation();
        // The pane handled this wheel gesture; without consuming, the skin would also jump.
        event.consume();
    }

    /** Eases to the very end of the content, e.g. after a new message was appended. */
    public void scrollToBottom() {
        scrollTo(1.0);
    }

    /** Eases to {@code vvalue} (clamped to the pane's range). */
    public void scrollTo(double vvalue) {
        double next = clamp(vvalue, 0, 1);
        if (Math.abs(next - target) < SETTLE_EPSILON) {
            return;
        }
        target = next;
        startAnimation();
    }

    /** Jumps without animation; used when the transcript is rebuilt and no motion is wanted. */
    public void jumpTo(double vvalue) {
        stopAnimation();
        target = clamp(vvalue, 0, 1);
        scrollPane.setVvalue(target);
    }

    /** True while the viewport is still easing towards its target. */
    public boolean isAnimating() {
        return animating;
    }

    /** True when the eased destination is the end of the content, i.e. "following the stream". */
    public boolean isTargetingBottom() {
        return target >= 1 - SETTLE_EPSILON;
    }

    private void startAnimation() {
        if (!animating) {
            animating = true;
            lastFrameNanos = 0;
            timer.start();
        }
    }

    private void stopAnimation() {
        if (animating) {
            animating = false;
            timer.stop();
        }
    }

    private void syncTargetToCurrent() {
        stopAnimation();
        target = scrollPane.getVvalue();
    }

    private void tick(long nowNanos) {
        if (lastFrameNanos == 0) {
            lastFrameNanos = nowNanos;
            return;
        }
        double elapsedSeconds = Math.min((nowNanos - lastFrameNanos) / 1_000_000_000.0, 0.05);
        lastFrameNanos = nowNanos;

        double current = scrollPane.getVvalue();
        double distance = target - current;
        if (Math.abs(distance) < SETTLE_EPSILON) {
            scrollPane.setVvalue(target);
            stopAnimation();
            return;
        }
        scrollPane.setVvalue(nextValue(current, target, elapsedSeconds));
    }

    /**
     * One easing step towards {@code target}. Package-private so the shape of the curve is pinned by
     * a test: it must move towards the target, never overshoot it, and never move backwards.
     */
    static double nextValue(double current, double target, double elapsedSeconds) {
        double factor = 1 - Math.exp(-EASING_RATE_PER_SECOND * Math.max(0, elapsedSeconds));
        double next = current + (target - current) * factor;
        if (target > current) {
            return Math.min(next, target);
        }
        return Math.max(next, target);
    }

    private double scrollablePixels() {
        Node content = scrollPane.getContent();
        if (content == null) {
            return 0;
        }
        double viewport = scrollPane.getViewportBounds().getHeight();
        double contentHeight = content.getLayoutBounds().getHeight();
        return Math.max(0, contentHeight - viewport);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
