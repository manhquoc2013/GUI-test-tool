package com.studio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the project tree against the scrollbar flicker caused by rows that outgrow the viewport.
 *
 * <p>A tree cell whose graphic is wider than the viewport makes VirtualFlow grow a horizontal
 * scrollbar. That bar consumes height, so the vertical scrollbar appears, both disappear on the next
 * layout pass, and hovering a row with a long title made the pair blink. The fix is a viewport-derived
 * cap on the row plus an ellipsized, shrinkable title label.</p>
 */
class TreeRowWidthTest {

    /** Tree width used by the geometry test: leaves room above the minimum row width. */
    private static final double WIDE_TREE_WIDTH = 300;
    /** Tree width narrow enough that the cap must fall back to its floor. */
    private static final double NARROW_TREE_WIDTH = 60;
    /** Scene size for the rendered-cell test. */
    private static final double SCENE_WIDTH = 320;
    private static final double SCENE_HEIGHT = 600;
    /** Scene wide enough for title + status pill + inline actions to coexist in one row. */
    private static final double WIDE_ROW_SCENE_WIDTH = 900;
    /** Scene narrow enough that the inline actions cannot fit next to the status pill. */
    private static final double NARROW_ROW_SCENE_WIDTH = 420;
    /** Tolerance for snapped layout values reported by JavaFX. */
    private static final double EPSILON = 0.5;
    /** Title long enough that an unconstrained label would exceed any reasonable sidebar. */
    private static final String LONG_TITLE = "Một tiêu đề rất dài ".repeat(20);

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized by another test class
        }
    }

    @Test
    void rowCapShrinksWithNestingAndNeverGoesBelowTheFloor() throws Exception {
        withExplorer((controller, tree) -> {
            tree.resize(WIDE_TREE_WIDTH, SCENE_HEIGHT);
            TreeItem<Object> suite = attachNestedItems(tree);

            Method cap = ExplorerPageController.class.getDeclaredMethod("treeRowWidthCap", TreeItem.class);
            cap.setAccessible(true);

            double topLevel = (double) cap.invoke(controller, suite);
            double nested = (double) cap.invoke(controller, suite.getChildren().get(0));

            // showRoot=false: a top-level row is still indented once below the hidden root, so its
            // chrome covers one disclosure indent as well — otherwise the cap overshoots by 18px
            // and the row can still widen the virtual flow.
            assertEquals(WIDE_TREE_WIDTH - 48 - 18, topLevel, EPSILON,
                    "A top-level row must leave room for padding, the arrow, indent and scrollbar");
            assertEquals(topLevel - 18, nested, EPSILON,
                    "Each nesting level must reserve the disclosure indent");

            tree.resize(NARROW_TREE_WIDTH, SCENE_HEIGHT);
            assertEquals(80, (double) cap.invoke(controller, suite), EPSILON,
                    "A sidebar narrower than the chrome budget must fall back to the minimum width");
        });
    }

    @Test
    void longTitleRowStaysInsideTheViewportAndEllipsizes() throws Exception {
        withExplorer((controller, tree, root) -> {
            new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
            root.applyCss();
            root.layout();

            TreeItem<Object> item = new TreeItem<>(LONG_TITLE);
            TreeItem<Object> nested = new TreeItem<>(LONG_TITLE);
            item.getChildren().add(nested);
            item.setExpanded(true);
            TreeItem<Object> treeRoot = new TreeItem<>("root");
            treeRoot.getChildren().add(item);
            tree.setRoot(treeRoot);
            root.applyCss();
            root.layout();

            TreeCell<?> cell = findRenderedCell(tree, item);
            HBox row = (HBox) cell.getGraphic();

            assertTrue(row.maxWidthProperty().get() <= tree.getWidth(),
                    "The row must never be wider than the tree, otherwise VirtualFlow adds a scrollbar");
            assertEquals(0.0, row.getMinWidth(), EPSILON);
            assertTrue(cell.prefWidth(-1) <= tree.getWidth() + EPSILON,
                    "The cell's preferred width drives the horizontal scrollbar; it must fit the viewport");

            TreeCell<?> nestedCell = findRenderedCell(tree, nested);
            assertTrue(nestedCell.prefWidth(-1) <= tree.getWidth() + EPSILON,
                    "A nested row must reserve its indent as well, or it would overflow the viewport");
            assertFalse(horizontalScrollBarIsVisible(tree),
                    "A long title must not spawn a horizontal scrollbar (it toggles the vertical one)");

            Label name = (Label) row.lookup(".tree-row-name");
            assertNotNull(name, "The row must expose its title label");
            assertEquals(0.0, name.getMinWidth(), EPSILON,
                    "A label's computed min width is its full text width; it must be released to shrink");
            assertEquals(OverrunStyle.ELLIPSIS, name.getTextOverrun(),
                    "A long title must truncate instead of forcing the cell wider");
            assertTrue(name.getWidth() <= row.getWidth(),
                    "The truncated title must fit inside the row");
        });
    }

    @Test
    void theStatusKeepsItsPlaceWhenTheInlineActionsAppear() throws Exception {
        withExplorer((controller, tree, root) -> {
            Scene scene = new Scene(root, WIDE_ROW_SCENE_WIDTH, SCENE_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());

            TreeCell<Object> cell = renderCaseRow(controller, tree, root);
            HBox row = (HBox) cell.getGraphic();
            Label name = (Label) row.lookup(".tree-row-name");
            Label status = (Label) row.lookup(".tree-row-status");
            HBox actions = (HBox) row.lookup(".tree-cell-actions");
            assertNotNull(status, "The row must expose its status pill");
            assertNotNull(actions, "The row must expose its inline actions");
            assertTrue(row.getChildren().indexOf(status) < row.getChildren().indexOf(actions),
                    "The status must precede the actions, so overflow can only clip the buttons");

            // Selecting reveals the inline buttons, the same path hovering takes.
            tree.getSelectionModel().select(cell.getTreeItem());
            root.applyCss();
            root.layout();

            assertTrue(actions.isManaged(), "A wide row shows the inline actions on selection");
            assertTrue(status.isVisible(), "Revealing the actions must not hide the status");
            assertTrue(maxX(status) <= maxX(cell) + EPSILON,
                    "The status must stay inside the row instead of being pushed out by the buttons");
            assertTrue(name.getWidth() > 0, "The title keeps a readable width next to the status");
        });
    }

    @Test
    void narrowRowsKeepTheStatusAndDropTheInlineActions() throws Exception {
        withExplorer((controller, tree, root) -> {
            Scene scene = new Scene(root, NARROW_ROW_SCENE_WIDTH, SCENE_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());

            TreeCell<Object> cell = renderCaseRow(controller, tree, root);
            HBox row = (HBox) cell.getGraphic();
            Label status = (Label) row.lookup(".tree-row-status");
            HBox actions = (HBox) row.lookup(".tree-cell-actions");

            tree.getSelectionModel().select(cell.getTreeItem());
            root.applyCss();
            root.layout();

            assertFalse(actions.isManaged(),
                    "Buttons that cannot fit are hidden: a clipped button is unusable, the context menu is not");
            assertTrue(status.isVisible(), "The status survives even when the actions do not fit");
            assertTrue(maxX(status) <= maxX(cell) + EPSILON, "The status stays inside the narrow row");
        });
    }

    /** Builds a group → suite → long-titled case tree and returns the rendered case cell. */
    private static TreeCell<Object> renderCaseRow(ExplorerPageController controller, TreeView<Object> tree,
            Parent root) throws Exception {
        TreeItem<Object> rootItem = new TreeItem<>("root");
        TreeItem<Object> suite = new TreeItem<>("Suite");
        TreeItem<Object> kase = new TreeItem<>(LONG_TITLE);
        rootItem.getChildren().add(suite);
        suite.getChildren().add(kase);
        suite.setExpanded(true);
        tree.setRoot(rootItem);
        registerRef(controller, suite,
                new ExplorerPageController.NodeRef("suite", "Suite", "ACTIVE", "root", "/tmp/suite", null, "/tmp/suite"));
        registerRef(controller, kase,
                new ExplorerPageController.NodeRef("case", "case", "READY", "Suite", "/tmp/suite", "case-1", null));
        root.applyCss();
        root.layout();
        return findRenderedCell(tree, kase);
    }

    private static void registerRef(ExplorerPageController controller, TreeItem<Object> item,
            ExplorerPageController.NodeRef ref) throws Exception {
        Method setter = ExplorerPageController.class.getDeclaredMethod("setNodeRef",
                TreeItem.class, ExplorerPageController.NodeRef.class);
        setter.setAccessible(true);
        setter.invoke(controller, item, ref);
    }

    /** Right edge in scene coordinates: layout bounds ignore hover shadows, so they are comparable. */
    private static double maxX(Node node) {
        return node.localToScene(node.getLayoutBounds()).getMaxX();
    }

    private static TreeItem<Object> attachNestedItems(TreeView<Object> tree) {        TreeItem<Object> root = new TreeItem<>("root");
        TreeItem<Object> suite = new TreeItem<>("suite");
        root.getChildren().add(suite);
        suite.getChildren().add(new TreeItem<>("case"));
        tree.setRoot(root);
        return suite;
    }

    /** Finds the cell the virtual flow rendered for {@code item}. */
    @SuppressWarnings("unchecked")
    private static TreeCell<Object> findRenderedCell(TreeView<Object> tree, TreeItem<Object> item) {
        for (Node node : tree.lookupAll(".tree-cell")) {
            if (node instanceof TreeCell<?> cell && cell.getTreeItem() == item
                    && cell.getGraphic() instanceof HBox) {
                return (TreeCell<Object>) cell;
            }
        }
        fail("The virtual flow did not render a row for the long title");
        return null;
    }

    /** @return true when the tree shows a horizontal scrollbar, the user-visible symptom of overflow. */
    private static boolean horizontalScrollBarIsVisible(TreeView<Object> tree) {
        for (Node node : tree.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.HORIZONTAL) {
                return bar.isVisible() && bar.getHeight() > 0;
            }
        }
        return false;
    }

    private static void withExplorer(FxAssertion assertion) throws Exception {
        withExplorer((controller, tree, root) -> assertion.run(controller, tree));
    }

    @SuppressWarnings("unchecked")
    private static void withExplorer(RootFxAssertion assertion) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(TreeRowWidthTest.class.getResource("/fxml/explorer_page.fxml"));
                loader.load();
                ExplorerPageController controller = loader.getController();
                Field treeField = ExplorerPageController.class.getDeclaredField("projectTree");
                treeField.setAccessible(true);
                assertion.run(controller, (TreeView<Object>) treeField.get(controller), loader.getRoot());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "The JavaFX thread did not finish the tree layout check");
        if (failure.get() != null) {
            fail("Tree layout check threw", failure.get());
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run(ExplorerPageController controller, TreeView<Object> tree) throws Exception;
    }

    @FunctionalInterface
    private interface RootFxAssertion {
        void run(ExplorerPageController controller, TreeView<Object> tree, Parent root) throws Exception;
    }
}
