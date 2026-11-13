package com.studio.controller;

import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the shell's top chrome: one 44px header with the identity cluster on the left and the action
 * cluster against the right edge, above a 34px document-tab strip.
 *
 * <p>The header used to be a FlowPane with left alignment. A FlowPane never grows a child, so the
 * action cluster sat directly after the breadcrumb and left roughly half the window width empty next
 * to a right-aligned status bar — the bar read as broken.</p>
 */
class MainShellChromeLayoutTest {

    private static final double HEADER_HEIGHT = 44;
    private static final double TAB_STRIP_HEIGHT = 34;
    private static final double HEADER_SIDE_PADDING = 14;
    /** Fixed sidebar width owned by the shell (see {@code MainShellController.SIDEBAR_EXPANDED}). */
    private static final double SIDEBAR_WIDTH = 220;
    private static final double EPSILON = 1.5;
    private static final double WIDE_WIDTH = 1200;
    private static final double MIN_WIDTH = 900;
    private static final double WINDOW_HEIGHT = 700;
    private static final String LONG_SUITE_NAME =
            "Suite_QuanLyNguoiDung_TheoVaiTro_KiemThu_ToanBoManHinh_2026";
    private static final double TAB_MAX_SANE_WIDTH = 320;
    private static final String DUMMY_TAB_ID = "SUITE:layout-test";

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

    @Test
    void theActionClusterIsPinnedToTheRightEdge(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            Node header = shell.lookup("#headerBar");
            Node identity = shell.lookup("#headerIdentity");
            Node actions = shell.lookup("#headerActions");

            assertEquals(sceneMaxX(header) - HEADER_SIDE_PADDING, sceneMaxX(actions), EPSILON,
                    "The action cluster must end at the header's right padding, not mid-bar");
            assertEquals(sceneMinX(header) + HEADER_SIDE_PADDING, sceneMinX(identity), EPSILON,
                    "The identity cluster starts at the header's left padding");
            assertTrue(sceneMinX(actions) - sceneMaxX(identity) > 100,
                    "The growing spacer keeps the two clusters apart at " + WIDE_WIDTH + "px");
        });
    }

    @Test
    void navigationLivesInOnePlaceOnly(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            assertNull(shell.root().lookup(".activity-rail"),
                    "The 48px activity rail repeated the sidebar's three destinations; it must stay removed");
            assertTrue(shell.root().lookupAll(".rail-btn").isEmpty(),
                    "No rail buttons may come back next to the sidebar");

            Node sidebar = shell.lookup("#sidebar");
            assertEquals(0, sceneMinX(sidebar), EPSILON,
                    "The single sidebar owns the left edge — there is no second nav column before it");
            assertEquals(3, sidebar.lookupAll(".sidebar-btn").size(),
                    "Exactly one sidebar entry per page (Explorer / Reports / AI Assistant)");
        });
    }

    @Test
    void theActiveSuiteIsShownInOnePlaceOnly(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            MainShellController.getInstance().setActiveWorkspace(LONG_SUITE_NAME, tempHome);
            shell.root().applyCss();
            layoutTree(shell.root());

            assertNull(shell.root().lookup("#activeSuiteLabel"),
                    "The header chip repeated the sidebar's \"SUITE ĐANG MỞ\" section");
            Label sidebarSuite = (Label) shell.lookup("#sidebarActiveSuite");
            assertEquals(LONG_SUITE_NAME, sidebarSuite.getText(),
                    "The sidebar is the single place that names the open suite");
        });
    }

    @Test
    void aLongSuiteNameEllipsizesInsideTheSidebar(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(MIN_WIDTH, "studio.css", shell -> {
            MainShellController.getInstance().setActiveWorkspace(LONG_SUITE_NAME, tempHome);
            shell.root().applyCss();
            layoutTree(shell.root());

            Label suiteLabel = (Label) shell.lookup("#sidebarActiveSuite");
            Node clearBtn = shell.lookup("#clearActiveSuiteBtn");
            Node sidebar = shell.lookup(".sidebar");

            assertEquals(SIDEBAR_WIDTH, ((Region) sidebar).getWidth(), EPSILON,
                    "A long suite name must not widen the fixed-width sidebar");
            assertTrue(sceneMaxX(suiteLabel) <= sceneMaxX(sidebar) + EPSILON,
                    "The suite name stays inside the panel and ellipsizes instead of overflowing");
            assertTrue(clearBtn.isVisible() && clearBtn.isManaged(),
                    "The clear button appears once a suite is open");
            assertTrue(sceneMaxX(clearBtn) <= sceneMaxX(sidebar) + EPSILON,
                    "The clear button must stay reachable, not be pushed out by a long name");
            assertTrue(suiteLabel.getWidth() < suiteLabel.prefWidth(-1),
                    "The label is narrower than its full text, i.e. it ellipsizes");
        });
    }

    @Test
    void theHeaderAndTabStripUseTheAgreedHeights(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            openDocumentTab(shell.root);
            assertEquals(HEADER_HEIGHT, ((Region) shell.lookup("#headerBar")).getHeight(), EPSILON,
                    "Header row height");
            assertEquals(TAB_STRIP_HEIGHT, ((Region) shell.lookup(".tab-strip-bar")).getHeight(), EPSILON,
                    "Tab strip height");
        });
    }

    @Test
    void pagesAreNotDuplicatedAsDocumentTabs(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            TabManager tabs = MainShellController.getInstance().getTabManager();
            assertNotNull(tabs, "The shell must expose its document tab manager");
            assertFalse(tabs.hasTabs(), "Explorer is a sidebar page, not an open document");
            assertNull(shell.root().lookup(".tab-item"),
                    "The sidebar page must not also be rendered as a tab");
            assertNotNull(shell.lookup(".explorer-page"),
                    "The page itself belongs in the content area, driven by the sidebar");

            Region strip = (Region) shell.lookup(".tab-strip-bar");
            assertFalse(strip.isManaged(),
                    "A visible but empty strip reads as a second, duplicate navigation bar");
            assertEquals(0, strip.getHeight(), EPSILON, "A hidden strip must take no vertical space");
        });
    }

    @Test
    void theStripAppearsOnlyWhileDocumentsAreOpen(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            Region strip = (Region) shell.lookup(".tab-strip-bar");
            Node content = shell.lookup("#contentArea");
            openDocumentTab(shell.root);
            assertTrue(strip.isManaged(), "Opening a suite must bring the document strip back");
            assertEquals(TAB_STRIP_HEIGHT, strip.getHeight(), EPSILON, "Strip height with a document");
            double contentYWithStrip = sceneMinY(content);

            TabManager tabs = MainShellController.getInstance().getTabManager();
            tabs.closeTab(DUMMY_TAB_ID);
            shell.root().applyCss();
            layoutTree(shell.root());

            assertFalse(strip.isManaged(), "Closing the last document hides the strip again");
            assertEquals(TAB_STRIP_HEIGHT, contentYWithStrip - sceneMinY(content), EPSILON,
                    "Hiding the strip must hand its 34px back to the page, not leave a blank bar");
            assertNotNull(shell.lookup(".explorer-page"),
                    "Closing the last tab returns to the page the sidebar had on screen");
        });
    }

    @Test
    void lightThemeKeepsTheSameChromeGeometry(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        double[] dark = new double[4];
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            openDocumentTab(shell.root);
            dark[0] = ((Region) shell.lookup("#headerBar")).getHeight();
            dark[1] = ((Region) shell.lookup(".tab-strip-bar")).getHeight();
            dark[2] = sceneMaxX(shell.lookup("#headerActions"));
            dark[3] = ((Region) shell.lookup(".tab-item")).getHeight();
        });
        withShell(WIDE_WIDTH, "studio-light.css", shell -> {
            openDocumentTab(shell.root);
            assertEquals(dark[0], ((Region) shell.lookup("#headerBar")).getHeight(), EPSILON,
                    "A theme swap must not change the header height");
            assertEquals(dark[1], ((Region) shell.lookup(".tab-strip-bar")).getHeight(), EPSILON,
                    "A theme swap must not change the tab strip height");
            assertEquals(dark[2], sceneMaxX(shell.lookup("#headerActions")), EPSILON,
                    "A theme swap must not move the actions: both sheets carry the structural rules");
            assertEquals(dark[3], ((Region) shell.lookup(".tab-item")).getHeight(), EPSILON,
                    "A theme swap must not change the tab height");
        });
    }

    @Test
    void tabsHugTheirContentAndFillTheStrip(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        withShell(WIDE_WIDTH, "studio.css", shell -> {
            openDocumentTab(shell.root);
            Region strip = (Region) shell.lookup(".tab-strip-bar");
            Region firstTab = (Region) shell.lookup(".tab-item");
            assertEquals(strip.getHeight() - 1, firstTab.getHeight(), EPSILON,
                    "Tabs fill the strip down to its 1px bottom rule instead of floating inside it");
            assertTrue(firstTab.getWidth() < TAB_MAX_SANE_WIDTH,
                    "A tab hugs its label; stretching tabs across the bar is what made the strip look empty");
        });
    }

    /** Opens one document tab, the same way a suite does, and settles the layout for measurement. */
    private static void openDocumentTab(Parent root) {
        MainShellController shell = MainShellController.getInstance();
        assertNotNull(shell, "The shell controller must be attached to the loaded FXML");
        TabManager tabs = shell.getTabManager();
        assertNotNull(tabs, "The shell must expose its document tab manager");
        tabs.openTab(DUMMY_TAB_ID, "Suite kiểm thử", "", new VBox(new Label("nội dung")), true, null, null);
        root.applyCss();
        layoutTree(root);
    }

    /** Loads the shell at {@code width}, lets CSS + layout settle, then runs the assertions on the FX thread. */
    private void withShell(double width, String stylesheet, Consumer<Shell> assertions) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                URL fxml = getClass().getResource("/fxml/main_shell.fxml");
                assertNotNull(fxml, "main_shell.fxml not found");
                FXMLLoader loader = new FXMLLoader(fxml);
                Parent root = loader.load();
                Scene scene = new Scene(root, width, WINDOW_HEIGHT);
                URL css = getClass().getResource("/css/" + stylesheet);
                assertNotNull(css, "stylesheet not found: " + stylesheet);
                scene.getStylesheets().add(css.toExternalForm());
                stage.setScene(scene);
                stage.show();
                // Two cycles: the scene's first layout pass runs before the sheet's heights are
                // resolved, so the strip's ScrollPane content is one pass behind.
                root.applyCss();
                layoutTree(root);
                root.applyCss();
                layoutTree(root);
                assertions.accept(new Shell(root));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out laying out the main shell");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("Main shell layout check threw: " + error.get().getMessage(), error.get());
        }
    }

    /**
     * {@code Parent.layout()} lays out one level only: a child resized by its parent stays stale until
     * its own {@code layout()} runs. Walking top-down settles the nested scroll/skin layers so the
     * measured geometry is what a real pulse produces.
     */
    private static void layoutTree(Node node) {
        if (node instanceof Parent parent) {
            parent.layout();
            for (Node child : parent.getChildrenUnmodifiable()) {
                layoutTree(child);
            }
        }
    }

    private static double sceneMinX(Node node) {
        return sceneBounds(node).getMinX();
    }

    private static double sceneMinY(Node node) {
        return sceneBounds(node).getMinY();
    }

    private static double sceneMaxX(Node node) {
        return sceneBounds(node).getMaxX();
    }

    /** Layout bounds (not bounds-in-parent), so theme effects/shadows cannot skew the measurement. */
    private static Bounds sceneBounds(Node node) {
        assertNotNull(node, "Expected node to be present in the shell");
        return node.localToScene(node.getLayoutBounds());
    }

    private record Shell(Parent root) {

        Node lookup(String selector) {
            Node node = root.lookup(selector);
            assertNotNull(node, "No node matches " + selector);
            return node;
        }
    }
}
