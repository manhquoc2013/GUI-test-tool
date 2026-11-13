package com.studio.ui;

import com.studio.model.TestStep;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class VisualStepFlowPaneTest {

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

    private void runFx(Runnable body) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for the FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("FX task threw", error.get());
        }
    }

    private static List<TestStep> threeSteps() {
        List<TestStep> steps = new ArrayList<>();
        steps.add(new TestStep(1, "NAVIGATE", "https://a.test", "", "", ""));
        steps.add(new TestStep(2, "CLICK", "#b", "", "", ""));
        steps.add(new TestStep(3, "FILL", "#c", "value", "", ""));
        return steps;
    }

    @Test
    void moveCardReordersAndRenumbersEveryStep() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());
            assertEquals(3, pane.getCardCount());

            pane.moveCard(0, 2);

            List<TestStep> steps = pane.getAllSteps();
            assertEquals(List.of("#b", "#c", "https://a.test"),
                    steps.stream().map(TestStep::getTarget).toList());
            assertEquals(List.of(1, 2, 3),
                    steps.stream().map(TestStep::getStepNumber).toList(),
                    "Step numbers must follow the visual order after a drag reorder");
        });
    }

    @Test
    void disabledStepsArePreservedButExcludedFromExecution() throws Exception {
        runFx(() -> {
            List<TestStep> steps = threeSteps();
            steps.get(1).setEnabled(false);
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(steps);

            assertEquals(3, pane.getAllSteps().size(), "Disabled steps must stay in the model");
            String script = StepScriptBidirectionalSync.stepsToScript(pane.getAllSteps(), "");
            assertFalse(script.contains("#b"),
                    "A disabled step must not be generated into the script");
            assertTrue(script.contains(StepScriptBidirectionalSync.DISABLED_STEP_PLACEHOLDER),
                    "It must still hold its line, or every later step number shifts");
        });
    }

    @Test
    void selectingACardDrivesTheCopilotTarget() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());
            assertNull(pane.getSelectedStep(), "Nothing is selected right after loading a case");

            StepCardView second = pane.getCardViews().get(1);
            second.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, false, false, false, null));

            assertSame(second.getStep(), pane.getSelectedStep());
            assertTrue(second.getStyleClass().contains("step-card-selected"));

            pane.updateSelectedStepTarget("button[type=submit]");
            assertEquals("button[type=submit]", second.getStep().getTarget());
        });
    }

    @Test
    void aStepCanBeInsertedDirectlyAfterAnotherStep() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());

            StepCardView first = pane.getCardViews().get(0);
            MenuButton overflow = (MenuButton) first.lookup(".step-overflow-btn");
            assertNotNull(overflow, "The card lost its overflow menu");
            overflow.getItems().stream()
                    .filter(item -> "Chèn bước bên dưới".equals(item.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The overflow menu lost the insert action"))
                    .fire();

            assertEquals(4, pane.getCardCount());
            assertEquals(List.of(1, 2, 3, 4),
                    pane.getAllSteps().stream().map(TestStep::getStepNumber).toList(),
                    "Inserting a step must renumber the steps that follow it");
            assertEquals("#b", pane.getAllSteps().get(2).getTarget(),
                    "The inserted step must land directly after its anchor, not at the end");
            assertEquals("CLICK", pane.getAllSteps().get(1).getAction(),
                    "The inserted step starts as the most common action, ready to edit");
            assertEquals("", pane.getAllSteps().get(1).getTarget(),
                    "The inserted step must not inherit the anchor's selector");
            assertTrue(pane.getCardViews().get(1).isExpanded(),
                    "A newly inserted step opens so it can be filled in immediately");
        });
    }

    @Test
    void appendedAssertionBecomesSelectedAndNotifies() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());
            int[] notifications = {0};
            pane.setOnStepsChanged(() -> notifications[0]++);

            TestStep assertion = new TestStep(0, "ASSERT_VISIBLE", ".welcome", "", "", "");
            pane.appendStep(assertion);

            assertEquals(4, pane.getCardCount());
            assertSame(assertion, pane.getSelectedStep());
            assertEquals(1, notifications[0]);
        });
    }

    // ── Drag reorder ────────────────────────────────────────────────────────

    @Test
    void dropIndexCountsTheSlotAfterTheDraggedCardIsRemoved() {
        assertEquals(2, VisualStepFlowPane.dropIndex(0, 2, true),
                "Dragging the first step below the last one lands it in the last slot");
        assertEquals(0, VisualStepFlowPane.dropIndex(2, 0, false),
                "Dragging the last step above the first one lands it in slot 0");
        assertEquals(1, VisualStepFlowPane.dropIndex(0, 1, true),
                "Dropping on the lower half of the next card swaps the two");
        assertEquals(VisualStepFlowPane.NO_DROP, VisualStepFlowPane.dropIndex(0, 1, false),
                "Dropping on the upper half of the next card is where the step already is");
        assertEquals(VisualStepFlowPane.NO_DROP, VisualStepFlowPane.dropIndex(1, 0, true));
        assertEquals(VisualStepFlowPane.NO_DROP, VisualStepFlowPane.dropIndex(-1, 0, true));
    }

    // ── Undo / redo ─────────────────────────────────────────────────────────

    @Test
    void aReorderCanBeUndoneAndRedone() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());
            assertFalse(pane.canUndo(), "A freshly loaded case has nothing to undo");

            pane.moveCard(0, 2);
            assertEquals(List.of("#b", "#c", "https://a.test"), targets(pane));
            assertTrue(pane.canUndo());
            assertFalse(pane.canRedo(), "A new edit must drop the redo branch");

            assertTrue(pane.undo());
            assertEquals(List.of("https://a.test", "#b", "#c"), targets(pane));
            assertEquals(List.of(1, 2, 3), numbers(pane), "Undo must renumber the steps");
            assertTrue(pane.canRedo());

            assertTrue(pane.redo());
            assertEquals(List.of("#b", "#c", "https://a.test"), targets(pane));
            assertFalse(pane.canRedo());
        });
    }

    @Test
    void aDeleteCanBeUndoneBackIntoItsOriginalSlot() throws Exception {
        runFx(() -> {
            List<TestStep> steps = new ArrayList<>();
            steps.add(new TestStep(1, "NAVIGATE", "https://a.test", "", "", ""));
            steps.add(new TestStep(2, "CLICK", "", "", "", ""));
            steps.add(new TestStep(3, "CLICK", "#c", "", "", ""));
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(steps);

            TestStep removed = pane.getCardViews().get(1).getStep();
            fireOverflowItem(pane.getCardViews().get(1), "Xoá bước");
            assertEquals(2, pane.getCardCount());
            assertTrue(pane.canUndo(), "Deleting a step must be undoable");

            assertTrue(pane.undo());
            assertEquals(3, pane.getCardCount());
            assertSame(removed, pane.getAllSteps().get(1),
                    "Undo must restore the deleted step at its old slot, not at the end");
            assertEquals(List.of(1, 2, 3), numbers(pane));

            assertTrue(pane.redo());
            assertEquals(2, pane.getCardCount());
            assertFalse(targets(pane).contains(""), "Redo must remove the restored step again");
        });
    }

    @Test
    void loadingAnotherCaseClearsTheHistory() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());
            pane.moveCard(0, 1);
            assertTrue(pane.canUndo());

            pane.loadSteps(threeSteps());

            assertFalse(pane.canUndo(), "Undoing across documents would corrupt the closed case");
            assertFalse(pane.canRedo());
        });
    }

    @Test
    void theFooterHistoryButtonsFollowTheStack() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());

            Button undo = (Button) pane.lookup(".step-undo-btn");
            Button redo = (Button) pane.lookup(".step-redo-btn");
            assertNotNull(undo, "The builder lost its undo button");
            assertNotNull(redo, "The builder lost its redo button");
            assertTrue(undo.isDisable(), "Nothing to undo on a fresh case");
            assertTrue(redo.isDisable());

            pane.moveCard(0, 1);
            assertFalse(undo.isDisable());

            pane.undo();
            assertTrue(undo.isDisable(), "Undoing the only edit empties the undo stack");
            assertFalse(redo.isDisable(), "The undone move is now the redo branch");
        });
    }

    @Test
    void thePlatformShortcutUndoesTheLastEdit() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(threeSteps());
            pane.moveCard(0, 1);

            // Both modifiers set: the shortcut is Meta on macOS and Control elsewhere.
            pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.Z,
                    false, true, false, true));

            assertEquals(List.of("https://a.test", "#b", "#c"), targets(pane));
        });
    }

    @Test
    void deletingTheLastStepDoesNotCountAsAnUndoableMove() throws Exception {
        runFx(() -> {
            VisualStepFlowPane pane = new VisualStepFlowPane();
            pane.loadSteps(List.of(new TestStep(1, "CLICK", "", "", "", "")));

            fireOverflowItem(pane.getCardViews().get(0), "Xoá bước");

            assertEquals(0, pane.getCardCount());
            assertTrue(pane.canUndo());
            assertTrue(pane.undo());
            assertEquals(1, pane.getCardCount(), "Undo must bring back the only step of the case");
        });
    }

    private static List<String> targets(VisualStepFlowPane pane) {
        return pane.getAllSteps().stream().map(TestStep::getTarget).toList();
    }

    private static List<Integer> numbers(VisualStepFlowPane pane) {
        return pane.getAllSteps().stream().map(TestStep::getStepNumber).toList();
    }

    private static void fireOverflowItem(StepCardView card, String label) {
        MenuButton overflow = (MenuButton) card.lookup(".step-overflow-btn");
        assertNotNull(overflow, "The card lost its overflow menu");
        overflow.getItems().stream()
                .filter(item -> label.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The overflow menu lost " + label))
                .fire();
    }
}
