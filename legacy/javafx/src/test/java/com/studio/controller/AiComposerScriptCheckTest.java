package com.studio.controller;

import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the composer's "Tạo Test Script" contract.
 *
 * <p>The box starts unticked and a typed URL alone must not turn it on: automation output is
 * opt-in unless the prompt itself asks for a test script / test case / "trường hợp kiểm thử".
 * The tooltip that explains the box also has to stay legible in the light theme — a tooltip's
 * styleable parent is the control that owns it, so control-scoped text colours used to leak in
 * and paint #0f172a text on the dark tooltip fill.</p>
 */
class AiComposerScriptCheckTest {

    private static final String DARK_CSS = "/css/studio.css";
    private static final String LIGHT_CSS = "/css/studio-light.css";
    private static final double MIN_CONTRAST_RATIO = 4.5;
    /** WCAG 1.4.11: a control outline needs 3:1 against its fill. */
    private static final double MIN_UI_CONTRAST_RATIO = 3.0;

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

    @AfterEach
    void tearDown() {
        StudioPaths.resetForTesting();
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
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void theBoxStartsUnticked(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Page page = loadPage(LIGHT_CSS);
            try {
                assertFalse(page.scriptCheck().isSelected(), "Automation generation is opt-in, not the default");
            } finally {
                page.close();
            }
        });
    }

    @Test
    void typingAnExplicitRequestTicksTheBox(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Page page = loadPage(LIGHT_CSS);
            try {
                for (String prompt : new String[] {
                        "tạo test script cho form login",
                        "tạo test case cho chức năng đăng nhập",
                        "sinh trường hợp kiểm thử từ tài liệu đính kèm",
                        "viết code test automation"}) {
                    page.prompt().setText(prompt);
                    assertTrue(page.scriptCheck().isSelected(),
                            "Typed request must turn automation on: " + prompt);
                }

                page.prompt().setText("đăng nhập với tài khoản admin");
                assertFalse(page.scriptCheck().isSelected(), "Dropping the request turns it back off");
            } finally {
                page.close();
            }
        });
    }

    @Test
    void aUrlOrScenarioWordingAloneLeavesTheBoxUnticked(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Page page = loadPage(LIGHT_CSS);
            try {
                page.prompt().setText("kiểm thử https://example.com/login");
                assertFalse(page.scriptCheck().isSelected(), "A URL is a target, not a request for a script");

                page.prompt().setText("tạo kịch bản kiểm thử cho màn hình sản phẩm");
                assertFalse(page.scriptCheck().isSelected(),
                        "Scenario-only output is a spec, not Playwright code");
            } finally {
                page.close();
            }
        });
    }

    @Test
    void aManualChoiceSurvivesTypingButAnExplicitRequestStillWins(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Page page = loadPage(LIGHT_CSS);
            try {
                page.scriptCheck().fire();
                assertTrue(page.scriptCheck().isSelected(), "The user can still opt in manually");
                page.prompt().setText("đăng nhập với tài khoản admin");
                assertTrue(page.scriptCheck().isSelected(), "Typing must not undo a manual tick");
                page.prompt().setText("tạo test case cho giỏ hàng");
                page.prompt().setText("đăng nhập với tài khoản admin");
                assertTrue(page.scriptCheck().isSelected(),
                        "A manually ticked box stays on even after the request is reworded");

                page.scriptCheck().fire();
                assertFalse(page.scriptCheck().isSelected());
                page.prompt().setText("mở trang chủ");
                assertFalse(page.scriptCheck().isSelected(), "Typing must not undo a manual untick");

                page.prompt().setText("tạo test script cho form login");
                assertTrue(page.scriptCheck().isSelected(),
                        "An explicit request turns automation back on, as asked");
                page.prompt().setText("mở trang chủ");
                assertFalse(page.scriptCheck().isSelected(),
                        "The auto tick is released once the request wording disappears");
            } finally {
                page.close();
            }
        });
    }

    @Test
    void theUncheckedBoxOutlineStaysVisibleInLightMode(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Page page = loadPage(LIGHT_CSS);
            try {
                Node box = page.root().lookup(".check-box .box");
                assertNotNull(box, "The checkbox must paint its box");
                assertTrue(box instanceof Region, "The checkbox box is a region");
                Region region = (Region) box;
                assertNotNull(region.getBorder(), "An unchecked box needs an outline");
                Color border = (Color) region.getBorder().getStrokes().get(0).getTopStroke();
                Color fill = region.getBackground() == null || region.getBackground().getFills().isEmpty()
                        ? Color.WHITE
                        : (Color) region.getBackground().getFills().get(0).getFill();
                double ratio = contrastRatio(border, fill);
                assertTrue(ratio >= MIN_UI_CONTRAST_RATIO,
                        "Unchecked box outline " + border + " on " + fill + " only reaches "
                                + String.format("%.2f", ratio) + ":1");
            } finally {
                page.close();
            }
        });
    }

    @Test
    void theTooltipStaysLegibleInBothThemes(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        for (String theme : new String[] {DARK_CSS, LIGHT_CSS}) {
            runFx(() -> {
                Page page = loadPage(theme);
                try {
                    Tooltip tip = page.scriptCheck().getTooltip();
                    assertNotNull(tip, "The script box must explain itself");
                    tip.show(page.scriptCheck(), 40, 40);
                    try {
                        Scene tipScene = tip.getScene();
                        assertNotNull(tipScene, "Tooltip must have a popup scene");
                        tipScene.getRoot().applyCss();
                        tipScene.getRoot().layout();
                        Label label = tipScene.getRoot().lookupAll(".tooltip").stream()
                                .filter(n -> n instanceof Label)
                                .map(n -> (Label) n)
                                .findFirst()
                                .orElse(null);
                        assertNotNull(label, "Tooltip must render a label");
                        Color text = (Color) label.getTextFill();
                        Color fill = tooltipFill(label);
                        double ratio = contrastRatio(text, fill);
                        assertTrue(ratio >= MIN_CONTRAST_RATIO,
                                "Tooltip text " + text + " on " + fill + " in " + theme
                                        + " only reaches " + String.format("%.2f", ratio) + ":1");
                    } finally {
                        tip.hide();
                    }
                } finally {
                    page.close();
                }
            });
        }
    }

    private static Page loadPage(String themeCss) {
        try {
            URL resource = AiComposerScriptCheckTest.class.getResource("/fxml/ai_assistant_page.fxml");
            assertNotNull(resource, "ai_assistant_page.fxml resource not found");
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Scene scene = new Scene(root, 1024, 700);
            scene.getStylesheets().setAll(
                    AiComposerScriptCheckTest.class.getResource(themeCss).toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            CheckBox box = (CheckBox) root.lookup("#autoGenerateScriptCheck");
            TextArea prompt = (TextArea) root.lookup("#promptInputArea");
            assertNotNull(box, "autoGenerateScriptCheck must exist");
            assertNotNull(prompt, "promptInputArea must exist");
            return new Page(root, box, prompt, stage);
        } catch (Exception e) {
            fail("Failed to load ai_assistant_page.fxml: " + e.getMessage(), e);
            return null;
        }
    }

    private static Color tooltipFill(Label label) {
        if (label.getBackground() == null || label.getBackground().getFills().isEmpty()) {
            return Color.TRANSPARENT;
        }
        BackgroundFill first = label.getBackground().getFills().get(0);
        return first.getFill() instanceof Color color ? color : Color.TRANSPARENT;
    }

    private static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private record Page(Parent root, CheckBox scriptCheck, TextArea prompt, Stage stage) {
        void close() {
            stage.close();
        }
    }
}
