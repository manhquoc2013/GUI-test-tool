package com.studio.controller;

import com.studio.model.TestSuite;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.util.UiMotion;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Static helper for page navigation within the main shell.
 */
public class ViewNavigator {

    public enum Page {
        EXPLORER("/fxml/explorer_page.fxml"),
        REPORTS("/fxml/reports_page.fxml"),
        AI_ASSISTANT("/fxml/ai_assistant_page.fxml");

        public final String fxmlPath;

        Page(String fxmlPath) {
            this.fxmlPath = fxmlPath;
        }
    }

    private static StackPane contentArea;
    private static TabManager tabManager;
    private static final Map<Page, Node> pageCache = new HashMap<>();
    private static final Map<Page, Object> controllerCache = new HashMap<>();
    private static Page currentPage;
    private static Consumer<Page> onPageNavigated;

    public static void init(StackPane content) {
        init(content, null);
    }

    public static void init(StackPane content, TabManager tm) {
        contentArea = content;
        tabManager = tm;
        pageCache.clear();
        controllerCache.clear();
    }

    public static void setTabManager(TabManager tm) {
        tabManager = tm;
    }

    public static TabManager getTabManager() {
        return tabManager;
    }

    /**
     * Pages are sidebar destinations, not documents: the shell listens here to keep the breadcrumb
     * and the sidebar/rail selection in sync with whatever page is on screen.
     */
    public static void setOnPageNavigated(Consumer<Page> listener) {
        onPageNavigated = listener;
    }

    public static Page getCurrentPage() {
        return currentPage;
    }

    /**
     * Shows a sidebar page. Deliberately does not create a document tab: the tab strip is reserved
     * for real documents (open suites, report details), otherwise every page would be listed twice —
     * once in the sidebar and once in the strip.
     */
    public static void navigateTo(Page page) {
        if (contentArea == null) return;
        try {
            // Always reload Reports so list UI/actions stay fresh after code changes
            if (page == Page.REPORTS) {
                invalidate(Page.REPORTS);
            }
            Node node = pageCache.get(page);
            if (node == null) {
                FXMLLoader loader = new FXMLLoader(ViewNavigator.class.getResource(page.fxmlPath));
                node = loader.load();
                pageCache.put(page, node);
                controllerCache.put(page, loader.getController());
            }
            if (tabManager != null) {
                // Leaving a document tab for a sidebar page: drop the strip's active highlight.
                tabManager.clearActive();
            }
            showPage(node);
            currentPage = page;
            onPageShown(page);
            notifyPageNavigated(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Re-displays the page that was on screen, e.g. after the last document tab closed. Falls back to
     * Explorer when no page was ever shown (the shell was sitting on a workspace).
     */
    public static void showCurrentPage() {
        Page page = currentPage;
        Node cached = page != null ? pageCache.get(page) : null;
        if (page == null || cached == null) {
            navigateTo(Page.EXPLORER);
            return;
        }
        if (tabManager != null) {
            tabManager.clearActive();
        }
        showPage(cached);
        onPageShown(page);
        notifyPageNavigated(page);
    }

    private static void notifyPageNavigated(Page page) {
        if (onPageNavigated != null) {
            onPageNavigated.accept(page);
        }
    }

    /** Drop a cached page so the next navigate reloads FXML/controller. */
    public static void invalidate(Page page) {
        if (page == null) return;
        pageCache.remove(page);
        controllerCache.remove(page);
    }

    private static void showPage(Node node) {
        if (contentArea == null || node == null) return;
        contentArea.getChildren().setAll(node);
        UiMotion.playPageEnter(node);
    }

    public static void onPageShown(Page page) {
        if (page == Page.EXPLORER) {
            ExplorerPageController explorer = getController(Page.EXPLORER, ExplorerPageController.class);
            if (explorer != null) {
                explorer.reloadFromDisk();
            }
            // Keep sticky «SUITE ĐANG MỞ» when leaving Workspace
        } else if (page == Page.REPORTS) {
            ReportsPageController reports = getController(Page.REPORTS, ReportsPageController.class);
            if (reports != null) {
                reports.reloadQuiet();
            }
        } else if (page == Page.AI_ASSISTANT) {
            AiAssistantPageController ai = getController(Page.AI_ASSISTANT, AiAssistantPageController.class);
            if (ai != null) {
                ai.refreshModelIndicator();
                ai.reloadTargets();
            }
        }
    }

    public static SuiteWorkspacePageController navigateToWorkspace(String groupPath, String suiteName) {
        return navigateToWorkspace(groupPath, suiteName, null);
    }

    public static SuiteWorkspacePageController navigateToWorkspace(String groupPath, String suiteName, Path suiteDir) {
        if (contentArea == null) return null;
        try {
            String tabId = "SUITE:" + (suiteDir != null ? suiteDir.toString() : suiteName);
            if (tabManager != null && tabManager.hasTab(tabId)) {
                tabManager.selectTab(tabId);
                TabManager.StudioTab tab = tabManager.getTab(tabId);
                if (tab != null && tab.getController() instanceof SuiteWorkspacePageController c) {
                    return c;
                }
            }

            FXMLLoader loader = new FXMLLoader(ViewNavigator.class.getResource("/fxml/suite_workspace_page.fxml"));
            Node node = loader.load();
            SuiteWorkspacePageController ctrl = loader.getController();

            if (tabManager != null) {
                tabManager.openTab(tabId, suiteName, "", node, true,
                        ctrl::detachRunProgressListener, ctrl);
            } else {
                showPage(node);
            }

            currentPage = null;
            MainShellController shell = MainShellController.getInstance();
            if (shell != null) {
                shell.setActiveWorkspace(suiteName, suiteDir);
            }
            return ctrl;
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            ToastService.error("❌ Không mở được workspace: " + root.getMessage());
            return null;
        }
    }

    /** Re-open the last Workspace suite recorded in the shell sidebar. */
    public static boolean reopenActiveWorkspace() {
        MainShellController shell = MainShellController.getInstance();
        if (shell == null) return false;
        Path suiteDir = shell.getActiveSuiteDir();
        String suiteName = shell.getActiveSuiteName();
        if (suiteDir == null || !Files.isDirectory(suiteDir)) {
            ToastService.warn("⚠️ Chưa có suite đang mở — mở một suite từ Khám phá");
            return false;
        }
        try {
            ProjectStore store = new ProjectStore();
            TestSuite suite = store.loadAndMigrateSuite(suiteDir);
            String name = suite.getName() != null ? suite.getName()
                    : (suiteName != null ? suiteName : suiteDir.getFileName().toString());
            Path parent = suiteDir.getParent();
            String groupLabel = parent != null ? parent.getFileName().toString() : null;
            SuiteWorkspacePageController ctrl = navigateToWorkspace(groupLabel, name, suiteDir);
            if (ctrl == null) return false;
            ctrl.loadSuite(suite, suiteDir, null);
            return true;
        } catch (Exception e) {
            ToastService.error("❌ Không mở lại được suite: " + e.getMessage());
            return false;
        }
    }

    public static ReportDetailPageController navigateToReportDetail(String runId) {
        if (contentArea == null) return null;
        try {
            String tabId = "REPORT:" + runId;
            if (tabManager != null && tabManager.hasTab(tabId)) {
                tabManager.selectTab(tabId);
                TabManager.StudioTab tab = tabManager.getTab(tabId);
                if (tab != null && tab.getController() instanceof ReportDetailPageController c) {
                    return c;
                }
            }

            FXMLLoader loader = new FXMLLoader(ViewNavigator.class.getResource("/fxml/report_detail_page.fxml"));
            Node node = loader.load();
            ReportDetailPageController ctrl = loader.getController();
            ctrl.loadReport(runId);

            if (tabManager != null) {
                String title = "Báo cáo " + (runId.length() > 8 ? runId.substring(runId.length() - 8) : runId);
                tabManager.openTab(tabId, title, "", node, true, null, ctrl);
            } else {
                showPage(node);
            }

            return ctrl;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T getController(Page page, Class<T> type) {
        Object controller = controllerCache.get(page);
        if (controller == null || type == null || !type.isInstance(controller)) {
            return null;
        }
        return type.cast(controller);
    }
}
