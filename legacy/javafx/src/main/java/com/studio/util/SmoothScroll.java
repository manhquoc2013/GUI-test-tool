package com.studio.util;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeView;
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
    /** A live value this far from the last one we wrote was moved by someone else. */
    private static final double EXTERNAL_MOVE_EPSILON = 1e-6;

    private final ScrollPane scrollPane;
    private final AnimationTimer timer;
    private double target;
    /** The value this controller last wrote, used to notice moves it did not make. */
    private double lastApplied;
    private double lastFrameNanos;
    private boolean animating;

    private SmoothScroll(ScrollPane scrollPane) {
        this.scrollPane = scrollPane;
        this.target = scrollPane.getVvalue();
        this.lastApplied = this.target;
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
        lastApplied = target;
        // A filter, not a handler: a ScrollPane's skin stops scroll events from bubbling out of its
        // viewport, so a handler on the pane never sees a wheel gesture made over its content. Without
        // the filter the skin jumps the viewport itself and this controller's target goes stale.
        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        // A thumb drag or a scrollbar click owns the value directly; fighting it would oscillate.
        scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> syncTargetToCurrent());
    }

    private void onScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0 || nestedControlOwnsGesture(event)) {
            return;
        }
        // A rebuild, a skin clamp or a scrollbar drag can move the pane without going through this
        // controller. Ease from where the viewport actually is, otherwise the first notch teleports.
        adoptExternalValue();
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
        // The target alone is not enough: the transcript can grow underneath a settled animation, so
        // the live value drifts away from a target that is still 1.0. Re-arming only when the *target*
        // moved made "follow the newest message" stop for the rest of the conversation.
        if (Math.abs(next - target) < SETTLE_EPSILON
                && Math.abs(next - scrollPane.getVvalue()) < SETTLE_EPSILON) {
            return;
        }
        target = next;
        startAnimation();
    }

    /** Jumps without animation; used when the transcript is rebuilt and no motion is wanted. */
    public void jumpTo(double vvalue) {
        stopAnimation();
        target = clamp(vvalue, 0, 1);
        lastApplied = target;
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

    /** Where the next frames are easing to; package-private so a test can pin a gesture's origin. */
    double targetValue() {
        return target;
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
        lastApplied = target;
    }

    /** Hands control back to the pane when its value changed without this controller writing it. */
    private void adoptExternalValue() {
        double current = scrollPane.getVvalue();
        if (Math.abs(current - lastApplied) <= EXTERNAL_MOVE_EPSILON) {
            return;
        }
        stopAnimation();
        target = current;
        lastApplied = current;
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
            lastApplied = target;
            stopAnimation();
            return;
        }
        double next = nextValue(current, target, elapsedSeconds);
        scrollPane.setVvalue(next);
        lastApplied = next;
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

    /**
     * True when the wheel gesture belongs to a control nested in the content (a list or a pane inside
     * a bubble). Such a control scrolls itself; easing the outer pane as well would move both.
     */
    private boolean nestedControlOwnsGesture(ScrollEvent event) {
        if (!(event.getTarget() instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null && current != scrollPane; current = current.getParent()) {
            if (current instanceof ScrollPane || current instanceof ListView || current instanceof TableView
                    || current instanceof TreeView) {
                return true;
            }
        }
        return false;
    }

    private double scrollablePixels() {        Node content = scrollPane.getContent();
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
