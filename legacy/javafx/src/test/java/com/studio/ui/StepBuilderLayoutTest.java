package com.studio.ui;

import com.studio.model.TestStep;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the No-Code builder against the "everything is an ellipsis" layout.
 *
 * <p>An HBox shrinks its children down to their minimum, and a Labeled's minimum is the width of
 * "…". A narrow editor therefore used to show "+ Mở …" / "+ …" for the quick-add buttons and "…"
 * instead of the step number, which makes the builder unreadable. The controls that carry meaning
 * now hold their preferred width and the toolbar wraps instead of squeezing.</p>
 */
class StepBuilderLayoutTest {

    private static final double EPSILON = 0.5;
    private static final double NARROW = 400;
    private static final double VERY_NARROW = 300;
    private static final double NARROW_CARD = 360;

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized by another test class.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void quickAddButtonsWrapInsteadOfTruncatingTheirLabels() throws Exception {
        withStepPane(NARROW, pane -> {
            List<Button> quickAdds = new ArrayList<>();
            for (Node node : pane.lookupAll(".step-add-bar .button")) {
                quickAdds.add((Button) node);
            }
            assertFalse(quickAdds.isEmpty(), "The step bar exposes no quick-add buttons");
            for (Button button : quickAdds) {
                assertFalse(button.getText().isEmpty(), "A quick-add button lost its label");
                assertTrue(button.getWidth() >= button.prefWidth(-1) - EPSILON,
                        "'" + button.getText() + "' is squeezed to " + button.getWidth()
                                + "px, below its " + button.prefWidth(-1) + "px label");
            }
        });
    }

    @Test
    void theCaptionKeepsItsFullText() throws Exception {
        withStepPane(NARROW, pane -> {
            Label caption = (Label) pane.lookup(".step-add-bar-title");
            assertNotNull(caption, "The step bar lost its caption");
            assertTrue(caption.isVisible(), "The caption fits at " + NARROW + "px and must stay");
            assertTrue(caption.getWidth() >= caption.prefWidth(-1) - EPSILON,
                    "The caption is clipped to " + caption.getWidth() + "px");
        });
    }

    /** Below the caption's useful width the buttons must not be stacked one per line. */
    @Test
    void aVeryNarrowBarDropsTheCaptionAndKeepsTheButtons() throws Exception {
        withStepPane(VERY_NARROW, pane -> {
            Label caption = (Label) pane.lookup(".step-add-bar-title");
            assertNotNull(caption, "The step bar lost its caption");
            assertFalse(caption.isVisible(),
                    "At " + VERY_NARROW + "px the caption would force one button per line");
            Button first = (Button) pane.lookup(".step-quick-adds .button");
            assertNotNull(first, "The quick-add row disappeared");
            assertTrue(first.getWidth() >= first.prefWidth(-1) - EPSILON,
                    "The first quick-add button is squeezed to " + first.getWidth() + "px");
        });
    }

    @Test
    void stepIdentityIsNeverEllipsized() throws Exception {
        withStepPane(NARROW_CARD, pane -> {
            assertEquals(4, pane.getCardCount(), "Fixture mismatch");
            for (StepCardView card : pane.getCardViews()) {
                // A note has no number badge and its own chip, so each card is probed for what it
                // actually renders instead of assuming one layout for every card.
                String chipSelector = card.isNote() ? ".step-note-chip" : ".step-card-header .step-action-chip";
                Region action = (Region) card.lookup(chipSelector);
                assertNotNull(action, "A step card lost its action picker");
                assertTrue(action.getWidth() >= action.prefWidth(-1) - EPSILON,
                        "The action chip is squeezed to " + action.getWidth() + "px");
                if (card.isNote()) {
                    continue;
                }
                Region badge = (Region) card.lookup(".step-card-header .step-num-badge");
                assertNotNull(badge, "A step card lost its number badge");
                assertTrue(badge.getWidth() >= badge.prefWidth(-1) - EPSILON,
                        "The step number is ellipsized to " + badge.getWidth() + "px");
            }
        });
    }

    /**
     * A recorded script is often one comment per action. Rendering each comment as a full card
     * pushed the real steps off screen, which is why a note collapses to a single editable line.
     */
    @Test
    void aCommentTakesOneLineInsteadOfACardHeight() throws Exception {
        withStepPane(NARROW_CARD, pane -> {
            StepCardView note = pane.getCardViews().get(1);
            StepCardView step = pane.getCardViews().get(2);
            assertTrue(note.isNote(), "Fixture mismatch: step 2 is a comment");
            assertFalse(step.isNote(), "Fixture mismatch: step 3 is a real step");
            assertTrue(note.getHeight() < step.getHeight() * 0.8,
                    "A comment is " + note.getHeight() + "px tall next to a " + step.getHeight() + "px step");
        });
    }

    @Test
    void cardsNeverGrowWiderThanTheViewport() throws Exception {
        withStepPane(NARROW_CARD, pane -> {
            Region viewport = (Region) pane.lookup(".scroll-pane");
            assertNotNull(viewport, "The step list lost its scroll pane");
            for (StepCardView card : pane.getCardViews()) {
                assertTrue(card.getWidth() <= viewport.getWidth() + EPSILON,
                        "A step card is " + card.getWidth() + "px wide inside a "
                                + viewport.getWidth() + "px pane, so it is clipped");
            }
        });
    }

    /** Undo/redo sit next to the primary add button, so they must not push it out of the footer. */
    @Test
    void theFooterKeepsEveryActionVisibleAtTheNarrowestWidth() throws Exception {
        withStepPane(VERY_NARROW, pane -> {
            Region footer = (Region) pane.lookup(".step-footer-bar");
            assertNotNull(footer, "The builder lost its footer bar");

            Button undo = (Button) pane.lookup(".step-undo-btn");
            Button redo = (Button) pane.lookup(".step-redo-btn");
            assertNotNull(undo, "The footer lost its undo button");
            assertNotNull(redo, "The footer lost its redo button");

            double needed = undo.prefWidth(-1) + redo.prefWidth(-1) + footer.getPadding().getLeft()
                    + footer.getPadding().getRight();
            assertTrue(needed < footer.getWidth(),
                    "History buttons (" + needed + "px) do not fit in a " + footer.getWidth() + "px footer");
        });
    }

    /**
     * The drop indicator only recolours one border edge: an indicator that changed the border width
     * would resize the card under the pointer and make the drag-over slot flicker. Geometry cannot be
     * asserted here (a CSS re-application during the probe moves every card), so this pins the rule.
     */
    @Test
    void theDropIndicatorNeverDeclaresABorderWidth() throws Exception {
        String css = new String(getClass().getResourceAsStream("/css/studio.css").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String selector : new String[]{".step-card-drop-before {", ".step-card-drop-after {"}) {
            int start = css.indexOf(selector);
            assertTrue(start > 0, "studio.css lost the rule " + selector);
            int end = css.indexOf('}', start);
            String rule = css.substring(start, end);
            assertFalse(rule.contains("-fx-border-width"),
                    selector + " must only recolour an edge, never resize the card");
            assertTrue(rule.contains("-fx-border-color"));
        }
    }

    private void withStepPane(double width, Consumer<VisualStepFlowPane> assertions) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                VisualStepFlowPane pane = new VisualStepFlowPane();
                pane.loadSteps(List.of(
                        new TestStep(1, "RAW", "}", "", "Bình luận / lệnh tùy chỉnh", ""),
                        new TestStep(2, "RAW", "// 3. Kiểm thử âm", "", "Bình luận / lệnh tùy chỉnh", ""),
                        new TestStep(3, "NAVIGATE", "https://example.com/login", "",
                                "Điều hướng tới trang đăng nhập quản trị", ""),
                        new TestStep(4, "FILL", "input#username", "admin",
                                "Nhập tài khoản quản trị viên", "")));

                VBox root = new VBox(pane);
                VBox.setVgrow(pane, javafx.scene.layout.Priority.ALWAYS);
                Scene scene = new Scene(root, width, 640);
                URL css = getClass().getResource("/css/studio.css");
                assertNotNull(css, "studio.css not found");
                scene.getStylesheets().add(css.toExternalForm());
                stage.setScene(scene);
                stage.show();
                settle(root);

                assertions.accept(pane);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out laying out the step builder");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError assertion) {
                throw assertion;
            }
            fail("Step builder layout probe threw", error.get());
        }
    }

    private static void settle(Parent root) {
        root.applyCss();
        layoutTree(root);
        root.applyCss();
        layoutTree(root);
    }

    private static void layoutTree(Node node) {
        if (node instanceof Parent parent) {
            parent.layout();
            for (Node child : parent.getChildrenUnmodifiable()) {
                layoutTree(child);
            }
        }
    }
}
