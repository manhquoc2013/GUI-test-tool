package com.studio.ui;

import com.studio.model.StepResult;
import com.studio.model.TestStep;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
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
 * A run has to be readable on the script itself.
 *
 * <p>Before this, a finished case only produced a report and a terminal log, so answering "which
 * step broke?" meant opening the generated Playwright. These tests pin the replacement contract: the
 * card whose number the runner traced carries the verdict, a failure states its reason without a
 * click, and editing the step drops a verdict that no longer describes it.</p>
 */
class StepRunFeedbackUxTest {

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
    void aPassedStepShowsAPassBadgeAndNoFailureRow() throws Exception {
        TestStep step = new TestStep(1, "FILL", "input#email", "admin", "", "");
        withCard(step, card -> {
            card.applyExecution(new StepResult(1, "page.locator(\"#email\").fill(\"admin\");",
                    StepResult.PASSED, 132, null));

            Label badge = badge(card);
            assertTrue(badge.isVisible(), "The verdict must be on the card, not hidden in a report");
            assertTrue(badge.getText().contains("✓"));
            assertTrue(badge.getStyleClass().contains(StepCardView.CSS_STATUS_PASSED));
            Node resultRow = card.lookup(".step-result-row");
            assertNotNull(resultRow);
            assertFalse(resultRow.isManaged(), "A passing step must not grow a reason row");
            assertTrue(badge.getTooltip().getText().contains("132ms"),
                    "The tooltip says how long the step took");
        });
    }

    @Test
    void aFailedStepSaysWhyWithoutBeingExpanded() throws Exception {
        TestStep step = new TestStep(1, "CLICK", "#submit", "", "", "");
        withCard(step, card -> {
            card.applyExecution(new StepResult(1, "page.locator(\"#submit\").click();",
                    StepResult.FAILED, 30000, "TimeoutError: waiting for locator(\"#submit\")"));

            Label badge = badge(card);
            assertTrue(badge.getText().contains("✗"));
            assertTrue(badge.getStyleClass().contains(StepCardView.CSS_STATUS_FAILED));

            Node resultRow = card.lookup(".step-result-row");
            assertNotNull(resultRow, "A failed step must expose its reason");
            assertTrue(resultRow.isManaged(), "The reason row must take height on a failure");
            Label reason = (Label) card.lookup(".step-result-line");
            assertNotNull(reason);
            assertTrue(reason.getText().contains("TimeoutError"), reason.getText());
            assertFalse(card.isExpanded(), "Reading the reason must not require expanding the card");
        });
    }

    @Test
    void aStepThatNeverRanSaysSoInsteadOfLookingPassed() throws Exception {
        TestStep step = new TestStep(1, "CLICK", "#submit", "", "", "");
        withCard(step, card -> {
            card.applyExecution(new StepResult(1, "page.locator(\"#submit\").click();",
                    StepResult.SKIPPED, 0, "Không chạy vì bước trước bị lỗi"));

            Label badge = badge(card);
            assertTrue(badge.isVisible());
            assertTrue(badge.getStyleClass().contains(StepCardView.CSS_STATUS_IDLE));
            assertFalse(badge.getStyleClass().contains(StepCardView.CSS_STATUS_PASSED),
                    "A skipped step must never be coloured as a pass");
            assertFalse(card.lookup(".step-result-row").isManaged(),
                    "Only a real failure claims the reason row");
        });
    }

    @Test
    void editingTheStepDropsTheVerdictItNoLongerDescribes() throws Exception {
        TestStep step = new TestStep(1, "CLICK", "#go", "", "", "");
        withCard(step, card -> {
            card.applyExecution(new StepResult(1, "page.locator(\"#go\").click();",
                    StepResult.PASSED, 10, null));
            assertTrue(badge(card).isVisible());

            javafx.scene.control.TextField target = (javafx.scene.control.TextField)
                    card.lookup(".step-target-block .step-field");
            assertNotNull(target);
            target.setText("#go-2");

            assertFalse(badge(card).isVisible(),
                    "A badge must not outlive the code it described");
        });
    }

    @Test
    void thePanePaintsEachVerdictOnTheCardWithTheSameNumber() throws Exception {
        withPane(pane -> {
            pane.loadSteps(List.of(
                    new TestStep(1, "NAVIGATE", "https://example.com", "", "", ""),
                    new TestStep(2, "FILL", "input#email", "admin", "", ""),
                    new TestStep(3, "CLICK", "#submit", "", "", "")));

            pane.applyStepResults(List.of(
                    new StepResult(1, "page.navigate(\"https://example.com\");", StepResult.PASSED, 20, null),
                    new StepResult(2, "page.locator(\"#email\").fill(\"admin\");", StepResult.FAILED, 30,
                            "TimeoutError"),
                    new StepResult(3, "page.locator(\"#submit\").click();", StepResult.SKIPPED, 0,
                            "Không chạy vì bước trước bị lỗi")));

            List<Node> cards = pane.lookupAll(".step-card").stream().toList();
            assertEquals(3, cards.size());
            assertTrue(hasStatus(cards.get(0), StepCardView.CSS_STATUS_PASSED));
            assertTrue(hasStatus(cards.get(1), StepCardView.CSS_STATUS_FAILED));
            assertTrue(hasStatus(cards.get(2), StepCardView.CSS_STATUS_IDLE));

            Label summary = (Label) pane.lookup(".step-run-summary");
            assertNotNull(summary);
            assertTrue(summary.isVisible(), "The footer must tally the run in one line");
            assertTrue(summary.getText().contains("1 đạt"), summary.getText());
            assertTrue(summary.getText().contains("1 lỗi"), summary.getText());
            assertTrue(summary.getText().contains("bước 2"),
                    "The footer points at the step to fix: " + summary.getText());
        });
    }

    /**
     * Mid-run the card must say "this one is executing", and the footer must read as progress rather
     * than as a verdict — otherwise a step still running looks like a result and the tally lies.
     */
    @Test
    void aRunningStepIsMarkedAndTheFooterReadsAsProgress() throws Exception {
        TestStep step = new TestStep(2, "CLICK", "#submit", "", "", "");
        withCard(step, card -> {
            card.applyExecution(new StepResult(2, "page.locator(\"#submit\").click();",
                    StepResult.RUNNING, 0, null));

            Label badge = badge(card);
            assertTrue(badge.isVisible());
            assertTrue(badge.getStyleClass().contains(StepCardView.CSS_STATUS_RUNNING));
            assertFalse(badge.getStyleClass().contains(StepCardView.CSS_STATUS_PASSED));
            assertFalse(badge.getTooltip().getText().contains("0ms"),
                    "A running step has no duration yet");
            assertFalse(card.lookup(".step-result-row").isManaged(),
                    "A running step has no failure reason to show");
        });

        withPane(pane -> {
            pane.loadSteps(List.of(
                    new TestStep(1, "NAVIGATE", "https://example.com", "", "", ""),
                    new TestStep(2, "CLICK", "#submit", "", "", ""),
                    new TestStep(3, "CLICK", "#done", "", "", "")));
            pane.applyStepResults(List.of(
                    new StepResult(1, "page.navigate(\"https://example.com\");", StepResult.PASSED, 20, null),
                    new StepResult(2, "page.locator(\"#submit\").click();", StepResult.RUNNING, 0, null)));

            Label summary = (Label) pane.lookup(".step-run-summary");
            assertNotNull(summary);
            assertTrue(summary.getText().contains("Đang chạy bước 2"), summary.getText());
            assertFalse(summary.getText().contains("Lần chạy gần nhất"),
                    "A run in progress is not a finished result: " + summary.getText());
            assertTrue(summary.getStyleClass().contains(StepCardView.CSS_STATUS_RUNNING));

            List<Node> cards = pane.lookupAll(".step-card").stream().toList();
            assertTrue(hasStatus(cards.get(1), StepCardView.CSS_STATUS_RUNNING));
            assertFalse(hasStatus(cards.get(2), StepCardView.CSS_STATUS_IDLE),
                    "A step not reached yet must stay blank while the run is still going");
        });
    }

    @Test
    void clearingOrReloadingRemovesEveryStaleVerdict() throws Exception {
        withPane(pane -> {
            pane.loadSteps(List.of(new TestStep(1, "CLICK", "#go", "", "", "")));
            pane.applyStepResults(List.of(
                    new StepResult(1, "page.locator(\"#go\").click();", StepResult.PASSED, 5, null)));
            assertTrue(pane.hasStepResults());

            pane.clearStepResults();
            assertFalse(pane.hasStepResults());
            Node card = pane.lookup(".step-card");
            assertNotNull(card);
            assertFalse(hasStatus(card, StepCardView.CSS_STATUS_PASSED));

            // Loading a different case is a different run: the old verdicts must not survive.
            pane.applyStepResults(List.of(
                    new StepResult(1, "page.locator(\"#go\").click();", StepResult.PASSED, 5, null)));
            pane.loadSteps(List.of(new TestStep(1, "CLICK", "#other", "", "", "")));
            assertFalse(pane.hasStepResults(),
                    "A reloaded case must not inherit the previous run's verdicts");
        });
    }

    /**
     * The badge joins the header row, which already holds the drag handle, the number, the action
     * picker and the chevron. If it does not fit, the action picker is what gets clipped — the one
     * control a tester needs to fix the step.
     */
    @Test
    void theBadgeFitsInsideTheHeaderEvenOnANarrowCard() throws Exception {
        TestStep step = new TestStep(1, "CLICK", "#submit", "", "", "");
        withCard(step, card -> {
            card.setPrefWidth(320);
            card.applyExecution(new StepResult(1, "page.locator(\"#submit\").click();",
                    StepResult.FAILED, 30000, "TimeoutError: waiting for locator(\"#submit\")"));
            card.applyCss();
            layoutTree(card);

            Node header = card.lookup(".step-card-header");
            assertNotNull(header, "The card lost its header row");
            javafx.geometry.Bounds badgeBox = badge(card).localToScene(badge(card).getBoundsInLocal());
            javafx.geometry.Bounds headerBox = header.localToScene(header.getBoundsInLocal());

            assertTrue(badgeBox.getWidth() > 0, "A zero-width badge is an invisible result");
            assertTrue(badgeBox.getMinX() >= headerBox.getMinX() - 1,
                    "The badge overflows the header on the left");
            assertTrue(badgeBox.getMaxX() <= headerBox.getMaxX() + 1,
                    "The badge overflows the header on the right: " + badgeBox + " vs " + headerBox);
        });
    }

    /** A failure reason may be long; it must wrap inside the card rather than widen it. */
    @Test
    void aLongFailureReasonWrapsInsideTheCard() throws Exception {
        TestStep step = new TestStep(1, "CLICK", "#submit", "", "", "");
        withCard(step, card -> {
            card.setPrefWidth(300);
            card.applyExecution(new StepResult(1, "page.locator(\"#submit\").click();",
                    StepResult.FAILED, 30000,
                    "TimeoutError: waiting for locator(\"#submit\") — phần tử bị che bởi lớp phủ cookie banner"));
            card.applyCss();
            layoutTree(card);

            Label reason = (Label) card.lookup(".step-result-line");
            assertNotNull(reason);
            assertTrue(reason.isWrapText(), "A one-line clamped reason hides the cause of the failure");
            assertTrue(reason.getHeight() > reason.getFont().getSize(),
                    "The reason took no height, so it is not readable");
            assertTrue(reason.getWidth() <= card.getWidth(),
                    "The reason row widened the card: " + reason.getWidth() + " > " + card.getWidth());
        });
    }

    private static boolean hasStatus(Node card, String styleClass) {
        Node badge = card.lookup(".step-status-badge");
        return badge != null && badge.isVisible() && badge.getStyleClass().contains(styleClass);
    }

    private static Label badge(StepCardView card) {
        Node node = card.lookup(".step-status-badge");
        assertNotNull(node, "The card lost its run-result badge");
        return (Label) node;
    }

    private void withCard(TestStep step, Consumer<StepCardView> assertions) throws Exception {
        onFx(root -> {
            StepCardView card = new StepCardView(step, view -> {
            }, () -> {
            }, s -> {
            }, () -> {
            });
            VBox box = new VBox(card);
            root.getChildren().add(box);
            root.applyCss();
            layoutTree(root);
            assertions.accept(card);
        });
    }

    private void withPane(Consumer<VisualStepFlowPane> assertions) throws Exception {
        onFx(root -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            root.getChildren().add(pane);
            root.applyCss();
            layoutTree(root);
            assertions.accept(pane);
        });
    }

    /** Builds the scene on the FX thread and surfaces any assertion failure to the test thread. */
    private void onFx(Consumer<VBox> work) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                VBox root = new VBox();
                Scene scene = new Scene(root, 720, 480);
                URL css = getClass().getResource("/css/studio.css");
                assertNotNull(css, "studio.css not found");
                scene.getStylesheets().add(css.toExternalForm());
                stage.setScene(scene);
                stage.show();
                work.accept(root);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out laying out the run feedback");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError assertion) {
                throw assertion;
            }
            fail("Run feedback probe threw", error.get());
        }
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
