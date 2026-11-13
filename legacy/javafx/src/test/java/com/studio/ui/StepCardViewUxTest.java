package com.studio.ui;

import com.studio.model.TestStep;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the authoring loop of a single step card.
 *
 * <p>Before the accordion the card always showed two rows of fields, including a disabled value
 * input for actions that have no value, and the action picker read "RAW"/"NAVIGATE". These tests
 * pin the replacement contract: a readable one-line summary, an editor that only renders the fields
 * the action consumes, and destructive actions out of the reading path.</p>
 */
class StepCardViewUxTest {

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
    void aCardStartsCollapsedAndSaysWhatTheStepDoes() throws Exception {
        withCard(new TestStep(1, "FILL", "input#email", "admin", "", ""), card -> {
            assertFalse(card.isExpanded(), "A loaded case must stay scannable: cards start collapsed");
            assertFalse(body(card).isManaged(), "The editor body must not reserve height while collapsed");

            MenuButton chip = chip(card);
            assertEquals("Nhập liệu", chip.getText(), "The chip must read as an action, not as a code");
            assertEquals("nhập «admin» vào input#email", summary(card).getText());
        });
    }

    @Test
    void expandingShowsOnlyTheFieldsTheActionConsumes() throws Exception {
        withCard(new TestStep(1, "CLICK", "button.submit", "", "", ""), card -> {
            clickHeader(card);
            assertTrue(card.isExpanded(), "Clicking the summary row opens the editor");
            assertTrue(block(card, ".step-target-block").isManaged());
            assertFalse(block(card, ".step-value-block").isManaged(),
                    "A click has no value, so its input must not take half the row");

            chooseAction(card, StepAction.ASSERT_TEXT);
            assertTrue(block(card, ".step-value-block").isManaged(),
                    "Asserting text needs the expected value field");
        });
    }

    @Test
    void choosingAnActionRewritesTheModelAndTheSummary() throws Exception {
        withCard(new TestStep(1, "RAW", "page.reload();", "", "", ""), card -> {
            chooseAction(card, StepAction.NAVIGATE);

            assertEquals("NAVIGATE", card.getStep().getAction(),
                    "The chip must persist the Playwright-facing action code");
            assertEquals("Đi tới trang", chip(card).getText());
            assertFalse(card.getStep().getAction().equals("RAW"));
            assertTrue(summary(card).getText().startsWith("mở"),
                    "Switching to NAVIGATE must re-render the summary, was: " + summary(card).getText());
            assertFalse(summary(card).getStyleClass().contains("step-summary-code"),
                    "A selector is not code: the monospace marker must not leak from the RAW step");
            assertTrue(card.isExpanded(),
                    "Choosing an action opens the editor for the fields it asks for");
        });
    }

    @Test
    void rawStepsKeepTheirStatementAndAreMarkedAsCode() throws Exception {
        withCard(new TestStep(1, "RAW", "page.waitForLoadState(LoadState.LOAD);", "",
                "Bình luận / lệnh tùy chỉnh", ""), card -> {
            assertEquals("Lệnh tự do", chip(card).getText());
            assertEquals("chờ trang tải xong", summary(card).getText(),
                    "A raw line the builder understands must read as a sentence, not as code");
            assertFalse(summary(card).getStyleClass().contains("step-summary-code"),
                    "A sentence is not code, so it must not be rendered in monospace");
            card.expand();
            TextField target = (TextField) card.lookup(".step-target-block .step-field");
            assertEquals("page.waitForLoadState(LoadState.LOAD);", target.getText(),
                    "The statement itself stays editable verbatim");
        });
    }

    @Test
    void aCommentBecomesOneLineAndKeepsItsPlaceInTheScript() throws Exception {
        withCard(new TestStep(1, "RAW", "// 3. Kiểm thử âm", "", "Bình luận / lệnh tùy chỉnh", ""), card -> {
            assertEquals(StepAction.NOTE.code(), card.getStep().getAction(),
                    "A comment is a note, not a raw statement");
            assertEquals("3. Kiểm thử âm", card.getStep().getTarget());
            assertEquals("Ghi chú", chip(card).getText());
            assertTrue(card.isNote(), "The card must render as a single note line");
            // No identity row, no summary row, no editor: a comment must not cost a step's height.
            assertFalse(card.lookup(".step-card-header").isManaged());
            assertFalse(card.lookup(".step-card-summary-row").isManaged());
            assertFalse(card.lookup(".step-body").isManaged());
            TextField note = (TextField) card.lookup(".step-note-field");
            assertNotNull(note, "A note must be editable in place");
            assertEquals("3. Kiểm thử âm", note.getText());
            assertEquals("// 3. Kiểm thử âm", StepScriptBidirectionalSync.statement(card.getStep()),
                    "Editing a note must still emit a Java comment");
        });
    }

    @Test
    void anUnreadableStatementFallsBackToItsCode() throws Exception {
        withCard(new TestStep(1, "RAW", "helper.setup(page, cfg);", "", "", ""), card -> {
            assertEquals("helper.setup(page, cfg);", summary(card).getText());
            assertTrue(summary(card).getStyleClass().contains("step-summary-code"),
                    "Verbatim statements must be visually distinguishable from selectors");
        });
    }

    @Test
    void secondaryActionsStayHiddenUntilTheCardIsSelected() throws Exception {
        withCard(new TestStep(1, "CLICK", "#go", "", "", ""), card -> {
            MenuButton overflow = (MenuButton) card.lookup(".step-card-summary-row .step-overflow-btn");
            assertNotNull(overflow, "The card lost its overflow menu");
            assertFalse(overflow.isVisible(), "Copy/delete must not sit in the reading path");

            card.setSelected(true);
            assertTrue(overflow.isVisible(), "Selecting a card reveals its secondary actions");
            assertTrue(card.getStyleClass().contains("step-card-selected"));

            card.setSelected(false);
            assertFalse(overflow.isVisible());
            assertFalse(card.getStyleClass().contains("step-card-selected"));
        });
    }

    @Test
    void deletingAnEmptyStepIsImmediate() throws Exception {
        List<TestStep> deleted = new ArrayList<>();
        withCard(new TestStep(1, "CLICK", "", "", "", ""), card -> {
            chooseOverflowItem(card, "Xoá bước");
            assertEquals(1, deleted.size(),
                    "An empty step carries nothing to lose, so it must not open a modal");
            assertSame(card.getStep(), deleted.get(0));
        }, deleted);
    }

    @Test
    void anUnfinishedStepIsFlaggedUntilItsFieldsAreFilled() throws Exception {
        withCard(new TestStep(1, "FILL", "input#email", "", "", ""), card -> {
            assertTrue(summary(card).getStyleClass().contains("step-summary-incomplete"),
                    "A step that cannot run yet must be visually flagged");
            assertEquals("nhập giá trị vào input#email", summary(card).getText());

            card.expand();
            card.getStep().setTestData("admin");
            card.refreshFromModel();

            assertFalse(summary(card).getStyleClass().contains("step-summary-incomplete"),
                    "Filling the missing value clears the flag");
        });
    }

    @Test
    void togglingABlockUpdatesTheModelAndTheVisualState() throws Exception {
        withCard(new TestStep(1, "CLICK", "#go", "", "", ""), card -> {
            card.toggleEnable();
            assertFalse(card.getStep().isEnabled());
            assertTrue(card.getStyleClass().contains("step-card-disabled"));

            card.toggleEnable();
            assertTrue(card.getStep().isEnabled());
            assertFalse(card.getStyleClass().contains("step-card-disabled"));
        });
    }

    @Test
    void refreshingFromTheModelDoesNotDuplicateOrDropAText() throws Exception {
        withCard(new TestStep(1, "FILL", "input#name", "an", "", ""), card -> {
            card.expand();
            card.getStep().setTarget("input#full-name");
            card.refreshFromModel();

            assertEquals("nhập «an» vào input#full-name", summary(card).getText());
        });
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    private void chooseAction(StepCardView card, StepAction action) {
        for (MenuItem item : chip(card).getItems()) {
            // Separators have no text and are skipped by the null-safe comparison.
            if (action.label().equals(item.getText())) {
                item.fire();
                return;
            }
        }
        fail("The action picker has no entry for " + action.label());
    }

    private void chooseOverflowItem(StepCardView card, String label) {
        MenuButton overflow = (MenuButton) card.lookup(".step-overflow-btn");
        for (MenuItem item : overflow.getItems()) {
            if (item.getText().equals(label)) {
                item.fire();
                return;
            }
        }
        fail("The overflow menu has no entry for " + label);
    }

    private static void clickHeader(StepCardView card) {
        Node header = card.lookup(".step-card-header");
        assertNotNull(header, "The card lost its summary row");
        header.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, false, false, false, null));
    }

    /**
     * The identity row's chip. Scoped deliberately: a note card renders its own chip, and an
     * unscoped lookup would silently drive the hidden one.
     */
    private static MenuButton chip(StepCardView card) {
        MenuButton chip = (MenuButton) card.lookup(".step-card-header .step-action-chip");
        assertNotNull(chip, "The card lost its action chip");
        return chip;
    }

    private static Label summary(StepCardView card) {
        Label label = (Label) card.lookup(".step-summary");
        assertNotNull(label, "The card lost its summary line");
        return label;
    }

    private static Node body(StepCardView card) {
        Node body = card.lookup(".step-body");
        assertNotNull(body, "The card lost its editor body");
        return body;
    }

    private static Node block(StepCardView card, String selector) {
        Node node = card.lookup(selector);
        assertNotNull(node, "The card lost its " + selector + " block");
        return node;
    }

    private void withCard(TestStep step, Consumer<StepCardView> assertions) throws Exception {
        withCard(step, assertions, new ArrayList<>());
    }

    private void withCard(TestStep step, Consumer<StepCardView> assertions, List<TestStep> deleted)
            throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                StepCardView card = new StepCardView(step,
                        view -> deleted.add(view.getStep()), () -> {
                }, s -> {
                }, () -> {
                });
                VBox root = new VBox(card);
                Scene scene = new Scene(root, 520, 220);
                URL css = getClass().getResource("/css/studio.css");
                assertNotNull(css, "studio.css not found");
                scene.getStylesheets().add(css.toExternalForm());
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                layoutTree(root);

                assertions.accept(card);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out laying out the step card");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError assertion) {
                throw assertion;
            }
            fail("Step card probe threw", error.get());
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
