package com.studio.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Shared enter/exit motion for dialogs, drawers, and sidebars.
 */
public final class UiMotion {

    public static final Duration FAST = Duration.millis(160);
    public static final Duration NORMAL = Duration.millis(220);
    public static final Duration DRAWER = Duration.millis(260);

    private static final String BUSY = "studio.uiMotionBusy";
    private static final String CACHED = "studio.uiMotionCached";

    private UiMotion() {}

    /**
     * Renders a moving subtree once into a texture for the duration of the animation. A page or
     * drawer holding a long transcript re-laid out and re-rasterized its whole content on every
     * frame of a translate/fade; with a cached bitmap the motion only moves pixels. The cache is
     * dropped when the animation finishes so text returns to native sharpness and live content
     * (a streaming bubble) repaints normally.
     */
    private static void cacheForMotion(Node node) {
        if (node == null) {
            return;
        }
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);
        node.getProperties().put(CACHED, Boolean.TRUE);
    }

    private static void dropMotionCache(Node node) {
        if (node != null && Boolean.TRUE.equals(node.getProperties().remove(CACHED))) {
            node.setCache(false);
            node.setCacheHint(CacheHint.DEFAULT);
        }
    }

    public static boolean isBusy(Node node) {
        return node != null && Boolean.TRUE.equals(node.getProperties().get(BUSY));
    }

    private static void markBusy(Node node, boolean busy) {
        if (node != null) {
            node.getProperties().put(BUSY, busy);
        }
    }

    /** Allow a new motion (e.g. exit) to take over an in-flight enter animation. */
    public static void clearBusy(Node node) {
        if (node != null) {
            node.getProperties().remove(BUSY);
            dropMotionCache(node);
        }
    }

    /** Fade + slight rise for modal content; fade for overlay. */
    public static void playDialogEnter(Node overlay, Node content, Runnable onDone) {
        if (overlay == null && content == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Node host = content != null ? content : overlay;
        if (isBusy(host)) return;
        markBusy(host, true);
        cacheForMotion(content);

        ParallelTransition all = new ParallelTransition();
        if (overlay != null) {
            overlay.setOpacity(0);
            FadeTransition fade = new FadeTransition(NORMAL, overlay);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);
            all.getChildren().add(fade);
        }
        if (content != null) {
            content.setOpacity(0);
            content.setScaleX(0.96);
            content.setScaleY(0.96);
            content.setTranslateY(12);
            FadeTransition fade = new FadeTransition(NORMAL, content);
            fade.setFromValue(0);
            fade.setToValue(1);
            ScaleTransition scale = new ScaleTransition(NORMAL, content);
            scale.setFromX(0.96);
            scale.setFromY(0.96);
            scale.setToX(1);
            scale.setToY(1);
            TranslateTransition slide = new TranslateTransition(NORMAL, content);
            slide.setFromY(12);
            slide.setToY(0);
            fade.setInterpolator(Interpolator.EASE_OUT);
            scale.setInterpolator(Interpolator.EASE_OUT);
            slide.setInterpolator(Interpolator.EASE_OUT);
            all.getChildren().addAll(fade, scale, slide);
        }
        all.setOnFinished(e -> {
            dropMotionCache(content);
            markBusy(host, false);
            if (onDone != null) onDone.run();
        });
        all.play();
    }

    public static void playDialogExit(Node overlay, Node content, Runnable onDone) {
        if (overlay == null && content == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Node host = content != null ? content : overlay;
        if (isBusy(host)) return;
        markBusy(host, true);
        cacheForMotion(content);

        ParallelTransition all = new ParallelTransition();
        if (overlay != null) {
            FadeTransition fade = new FadeTransition(FAST, overlay);
            fade.setFromValue(overlay.getOpacity());
            fade.setToValue(0);
            fade.setInterpolator(Interpolator.EASE_IN);
            all.getChildren().add(fade);
        }
        if (content != null) {
            FadeTransition fade = new FadeTransition(FAST, content);
            fade.setFromValue(content.getOpacity());
            fade.setToValue(0);
            ScaleTransition scale = new ScaleTransition(FAST, content);
            scale.setToX(0.97);
            scale.setToY(0.97);
            TranslateTransition slide = new TranslateTransition(FAST, content);
            slide.setToY(8);
            fade.setInterpolator(Interpolator.EASE_IN);
            scale.setInterpolator(Interpolator.EASE_IN);
            slide.setInterpolator(Interpolator.EASE_IN);
            all.getChildren().addAll(fade, scale, slide);
        }
        all.setOnFinished(e -> {
            dropMotionCache(content);
            markBusy(host, false);
            if (onDone != null) onDone.run();
        });
        all.play();
    }

    /** Right drawer: overlay fade + panel slide from right. */
    public static void playDrawerOpen(Node overlay, Node drawer, Runnable onDone) {
        if (drawer == null) {
            if (onDone != null) onDone.run();
            return;
        }
        if (isBusy(drawer)) return;
        markBusy(drawer, true);
        cacheForMotion(drawer);

        double width = drawer.getBoundsInParent().getWidth();
        if (width <= 1 && drawer instanceof Region r) {
            width = Math.max(r.getPrefWidth(), 360);
        }
        if (width <= 1) width = 360;

        drawer.setTranslateX(width);
        drawer.setOpacity(1);
        if (overlay != null) {
            overlay.setOpacity(0);
        }

        ParallelTransition all = new ParallelTransition();
        TranslateTransition slide = new TranslateTransition(DRAWER, drawer);
        slide.setFromX(width);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        all.getChildren().add(slide);
        if (overlay != null) {
            FadeTransition fade = new FadeTransition(DRAWER, overlay);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);
            all.getChildren().add(fade);
        }
        all.setOnFinished(e -> {
            dropMotionCache(drawer);
            markBusy(drawer, false);
            if (onDone != null) onDone.run();
        });
        all.play();
    }

    public static void playDrawerClose(Node overlay, Node drawer, Runnable onDone) {
        if (drawer == null) {
            if (onDone != null) onDone.run();
            return;
        }
        if (isBusy(drawer)) return;
        markBusy(drawer, true);
        cacheForMotion(drawer);

        double width = drawer.getBoundsInParent().getWidth();
        if (width <= 1 && drawer instanceof Region r) {
            width = Math.max(r.getWidth(), Math.max(r.getPrefWidth(), 360));
        }
        if (width <= 1) width = 360;

        ParallelTransition all = new ParallelTransition();
        TranslateTransition slide = new TranslateTransition(DRAWER, drawer);
        slide.setToX(width);
        slide.setInterpolator(Interpolator.EASE_IN);
        all.getChildren().add(slide);
        if (overlay != null) {
            FadeTransition fade = new FadeTransition(DRAWER, overlay);
            fade.setToValue(0);
            fade.setInterpolator(Interpolator.EASE_IN);
            all.getChildren().add(fade);
        }
        all.setOnFinished(e -> {
            drawer.setTranslateX(0);
            dropMotionCache(drawer);
            markBusy(drawer, false);
            if (onDone != null) onDone.run();
        });
        all.play();
    }

    /** Animate region width properties (left sidebar). */
    public static void animateWidth(Region region, double toWidth, Runnable onMid, Runnable onDone) {
        if (region == null) {
            if (onDone != null) onDone.run();
            return;
        }
        if (isBusy(region)) return;
        markBusy(region, true);

        double from = region.getWidth() > 1 ? region.getWidth() : region.getPrefWidth();
        DoubleProperty proxy = new javafx.beans.property.SimpleDoubleProperty(from);
        proxy.addListener((obs, o, n) -> {
            double w = n.doubleValue();
            region.setPrefWidth(w);
            region.setMinWidth(w);
            region.setMaxWidth(w);
        });

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(proxy, from)),
                new KeyFrame(Duration.millis(80), e -> {
                    if (onMid != null) onMid.run();
                }),
                new KeyFrame(NORMAL, new KeyValue(proxy, toWidth, Interpolator.EASE_BOTH))
        );
        timeline.setOnFinished(e -> {
            region.setPrefWidth(toWidth);
            region.setMinWidth(toWidth);
            region.setMaxWidth(toWidth);
            markBusy(region, false);
            if (onDone != null) onDone.run();
        });
        timeline.play();
    }

    private static final String PULSE_KEY = "studio.uiPulse";

    /** Page enter: fade in + slight rise (matches dialog motion language). */
    public static void playPageEnter(Node page) {
        playPageEnter(page, null);
    }

    public static void playPageEnter(Node page, Runnable onDone) {
        if (page == null) {
            if (onDone != null) onDone.run();
            return;
        }
        if (isBusy(page)) {
            if (onDone != null) onDone.run();
            return;
        }
        markBusy(page, true);
        cacheForMotion(page);
        page.setOpacity(0);
        page.setTranslateY(16);

        ParallelTransition all = new ParallelTransition();
        FadeTransition fade = new FadeTransition(NORMAL, page);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition slide = new TranslateTransition(NORMAL, page);
        slide.setFromY(16);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        all.getChildren().addAll(fade, slide);
        all.setOnFinished(e -> {
            page.setOpacity(1);
            page.setTranslateY(0);
            dropMotionCache(page);
            markBusy(page, false);
            if (onDone != null) onDone.run();
        });
        all.play();
    }

    /** Soft opacity pulse while a row shows RUNNING. */
    public static void startPulse(Node node) {
        if (node == null) return;
        stopPulse(node);
        FadeTransition ft = new FadeTransition(Duration.millis(650), node);
        ft.setFromValue(1.0);
        ft.setToValue(0.4);
        ft.setAutoReverse(true);
        ft.setCycleCount(Timeline.INDEFINITE);
        node.getProperties().put(PULSE_KEY, ft);
        ft.play();
    }

    public static void stopPulse(Node node) {
        if (node == null) return;
        Object anim = node.getProperties().remove(PULSE_KEY);
        if (anim instanceof FadeTransition ft) {
            ft.stop();
        }
        node.setOpacity(1.0);
    }
}
