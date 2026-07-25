package com.studio.controller;

import com.studio.service.RunCancelService;
import com.studio.service.StatusBarService;
import com.studio.service.ToastService;
import com.studio.store.SettingsStore;
import com.studio.util.BusyButtons;
import com.studio.util.UiMotion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

/**
 * Main shell controller — sidebar, navigation, toast, status bar, run-all.
 */
public class MainShellController implements Initializable {

    private static MainShellController instance;

    private static final double SIDEBAR_EXPANDED = 220;
    private static final double SIDEBAR_COLLAPSED = 56;

    @FXML private StackPane contentArea;
    @FXML private StackPane shellStack;
    @FXML private VBox sidebar;
    @FXML private Button navExplorerBtn;
    @FXML private Button navReportsBtn;
    @FXML private Label activeSuiteLabel;
    @FXML private Label sidebarSuiteSectionLabel;
    @FXML private HBox sidebarActiveSuiteRow;
    @FXML private Label sidebarActiveSuite;
    @FXML private Button clearActiveSuiteBtn;
    @FXML private Separator sidebarSep;
    @FXML private Button runAllBtn;
    @FXML private Button stopRunBtn;
    @FXML private Button toggleSidebarBtn;
    @FXML private Button helpBtn;
    @FXML private StackPane helpLayer;
    @FXML private Region helpScrim;
    @FXML private VBox helpDrawer;
    @FXML private VBox helpContent;
    @FXML private Button helpCloseBtn;
    @FXML private Label statusLabel;
    @FXML private Label browserLabel;
    @FXML private VBox toastContainer;

    private boolean sidebarExpanded = true;
    private boolean helpVisible = false;
    private String activeSuiteName;
    private Path activeSuiteDir;

    public static MainShellController getInstance() {
        return instance;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;
        ToastService.setToastContainer(toastContainer);
        StatusBarService.setStatusLabel(statusLabel);
        ViewNavigator.init(contentArea);
        ViewNavigator.navigateTo(ViewNavigator.Page.EXPLORER);
        setActiveNavButton(navExplorerBtn);
        setActiveWorkspace(null, null);
        StatusBarService.ready();
        refreshBrowserLabel();
        applySidebarExpanded();
        wireHelpPanel();
        buildHelpContent();
        setHelpVisible(false);
        installTip(toggleSidebarBtn, "Thu gọn / mở rộng thanh bên");
        installTip(helpCloseBtn, "Đóng trợ giúp");
        installTip(stopRunBtn, "Dừng chạy test ngay");
        wireActiveSuiteShortcut();
        if (clearActiveSuiteBtn != null) {
            installTip(clearActiveSuiteBtn, "Bỏ suite đang mở");
        }
        RunCancelService.addListener(this::updateStopRunButton);
        updateStopRunButton();
    }

    private void wireActiveSuiteShortcut() {
        if (sidebarActiveSuite != null) {
            sidebarActiveSuite.setOnMouseClicked(e -> {
                if (e.getClickCount() >= 1 && activeSuiteDir != null) {
                    ViewNavigator.reopenActiveWorkspace();
                    e.consume();
                }
            });
        }
        if (activeSuiteLabel != null) {
            activeSuiteLabel.setOnMouseClicked(e -> {
                if (activeSuiteDir != null) {
                    ViewNavigator.reopenActiveWorkspace();
                    e.consume();
                }
            });
        }
    }

    @FXML
    private void clearActiveSuite() {
        boolean wasOnWorkspace = ViewNavigator.getCurrentPage() == null && activeSuiteDir != null;
        setActiveWorkspace(null, null);
        if (wasOnWorkspace) {
            ViewNavigator.navigateTo(ViewNavigator.Page.EXPLORER);
            setActiveNavButton(navExplorerBtn);
        }
        ToastService.info("📌 Đã bỏ suite đang mở");
    }

    private void wireHelpPanel() {
        if (helpScrim != null) {
            helpScrim.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                hideHelp();
                e.consume();
            });
        }
        if (helpDrawer != null) {
            helpDrawer.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        }
        if (helpCloseBtn != null) {
            helpCloseBtn.setOnAction(e -> hideHelp());
        }
        if (helpLayer != null) {
            helpLayer.widthProperty().addListener((obs, o, n) -> applyHelpDrawerWidth(n.doubleValue()));
        }
    }

    /** Help drawer uses ~2/3 of the content area width. */
    private void applyHelpDrawerWidth(double layerWidth) {
        if (helpDrawer == null || layerWidth <= 0) return;
        double w = Math.max(420, layerWidth * (2.0 / 3.0));
        helpDrawer.setPrefWidth(w);
        helpDrawer.setMinWidth(w);
        helpDrawer.setMaxWidth(w);
    }

    private void buildHelpContent() {
        if (helpContent == null) return;
        VBox body = helpContent;

        body.getChildren().add(section("🦾 Giới thiệu"));
        body.getChildren().add(bodyText(
                "Playwright Automation Studio giúp bạn tạo và chạy test tự động cho web "
                        + "mà không cần viết code thủ công. Ứng dụng dùng Playwright để "
                        + "ghi hình thao tác và chạy lại trên nhiều trình duyệt."));

        body.getChildren().add(section("📌 Thanh bên & thanh trên"));
        body.getChildren().add(bodyText(
                "🧭 Khám phá — quản lý cây dự án (Nhóm → Suite → Case)\n"
                        + "📊 Báo cáo — xem lịch sử các lần chạy test\n"
                        + "▶️ Chạy tất cả — chạy toàn bộ project\n"
                        + "📌 Suite đang mở — nhớ suite Workspace gần nhất; click mở lại, ✕ để bỏ\n"
                        + "⏹ Dừng — hiện trên thanh trên khi đang chạy; hủy ngay lượt chạy\n"
                        + "❓ Trợ giúp — mở/đóng ngăn hướng dẫn này\n"
                        + "☰ — thu gọn / mở rộng thanh bên"));

        body.getChildren().add(section("🧭 Explorer — Quản lý dự án"));
        body.getChildren().add(bodyText(
                "Cây 3 cấp: Nhóm (📁) → Suite (📋) → Case (🧪)\n\n"
                        + "• Click chọn → xem thông tin bên phải\n"
                        + "• Double-click → mở Workspace\n"
                        + "• Chuột phải → menu: Sửa, Xóa, Sao chép, Chạy\n"
                        + "• Kéo thả → di chuyển Suite / sắp xếp Case\n"
                        + "• Bộ lọc trạng thái → lọc theo ACTIVE / DRAFT / INACTIVE\n\n"
                        + "Panel Xem trước bên phải cho phép sửa mô tả, "
                        + "trạng thái, trình duyệt và nhấn Lưu."));

        body.getChildren().add(section("📝 Workspace — Làm việc chính"));
        body.getChildren().add(bodyText(
                "Mở bằng double-click vào Suite hoặc Case.\n\n"
                        + "Tab Code — xem & sửa script Java:\n"
                        + "  • 💾 Lưu code — lưu file .java\n"
                        + "  • Dấu * trên tab = code chưa lưu\n\n"
                        + "Tab Asserts — quản lý assertion:\n"
                        + "  • Lọc theo nhóm assertion\n"
                        + "  • Sửa / Xóa từng assertion\n"
                        + "  • [+ Thêm Assert] mở Assertion Builder"));

        body.getChildren().add(section("🔴 Ghi hình (Record)"));
        body.getChildren().add(bodyText(
                "1. Chọn Case trong danh sách\n"
                        + "2. Nhập URL bắt đầu (vd: https://example.com)\n"
                        + "3. Nhấn «🔴 Ghi hình lại»\n"
                        + "4. Thao tác trên trình duyệt Codegen\n"
                        + "5. Đóng trình duyệt → script được lưu\n"
                        + "6. Assertion Builder tự động mở\n\n"
                        + "⚠️ Cần Node.js + Playwright CLI. "
                        + "Portable bundle đã tích hợp sẵn."));

        body.getChildren().add(section("▶️ Chạy test"));
        body.getChildren().add(bodyText(
                "4 phạm vi chạy:\n"
                        + "  • Tất cả — sidebar ▶️ Chạy tất cả\n"
                        + "  • Nhóm — chuột phải vào Nhóm\n"
                        + "  • Suite — nút ▶️ Chạy Suite\n"
                        + "  • Case — nút ▶️ bên cạnh tên case\n\n"
                        + "Tùy chọn:\n"
                        + "  • ☐ Dừng khi lỗi đầu tiên\n"
                        + "  • ☐ Chung trình duyệt (cookie/session dùng chung)\n\n"
                        + "Điều kiện chạy:\n"
                        + "  • Suite phải ACTIVE\n"
                        + "  • Case phải Ready (đã ghi hình)\n"
                        + "  • Phải có ít nhất 1 assertion ACTIVE"));

        body.getChildren().add(section("✅ Assertion Builder"));
        body.getChildren().add(bodyText(
                "46 loại assertion, 8 nhóm:\n"
                        + "Nội dung trang, Phần tử, Ảnh & Media, "
                        + "File & Download, Bảng, Form & Input, "
                        + "Trang & Mạng, JS Tùy chỉnh.\n\n"
                        + "Mỗi assertion có 3 trạng thái:\n"
                        + "  🟢 ACTIVE — sẽ được chạy\n"
                        + "  📝 DRAFT — nháp, không chạy\n"
                        + "  ⏸️ INACTIVE — tắt, không chạy\n\n"
                        + "Chỉ ACTIVE assertion mới được thực thi. "
                        + "Kéo thả để sắp xếp thứ tự."));

        body.getChildren().add(section("📊 Báo cáo & Xuất Excel"));
        body.getChildren().add(bodyText(
                "Trang Báo cáo hiển thị lịch sử chạy:\n"
                        + "  • Passed / Failed / Skipped\n"
                        + "  • Thời gian, trình duyệt\n"
                        + "  • Lọc theo phạm vi & kết quả\n\n"
                        + "Trang Chi tiết:\n"
                        + "  • Bảng Cases với trạng thái\n"
                        + "  • Bảng Asserts với kết quả từng cái\n"
                        + "  • Ảnh chụp màn hình (PASSED/FAILED)\n"
                        + "  • 📥 Xuất Excel — 3 sheet "
                        + "(Tổng quan, Cases, Asserts)"));

        body.getChildren().add(section("💡 Mẹo nhanh"));
        body.getChildren().add(bodyText(
                "• URL tự động điền từ script sau khi ghi hình\n"
                        + "• Click lại vào node đã chọn để bỏ chọn\n"
                        + "• Code chưa lưu → tab hiện dấu * → chuyển "
                        + "case sẽ hỏi xác nhận\n"
                        + "• Kéo Suite thả vào Nhóm khác để di chuyển\n"
                        + "• Có thể sửa metadata Suite/Case ngay trên "
                        + "Explorer không cần mở Workspace\n"
                        + "• Terminal tự hiện khi Record/Run, ấn Ẩn để đóng"));
    }

    private static javafx.scene.Node section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("help-drawer-section");
        label.setWrapText(true);
        return label;
    }

    private static javafx.scene.Node bodyText(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("help-dialog-body");
        label.setWrapText(true);
        return label;
    }

    @FXML
    private void toggleHelp() {
        setHelpVisible(!helpVisible);
    }

    @FXML
    private void hideHelp() {
        setHelpVisible(false);
    }

    private void setHelpVisible(boolean show) {
        if (helpLayer == null || helpDrawer == null) {
            helpVisible = show;
            return;
        }
        if (show == helpVisible && helpLayer.isVisible() == show) {
            return;
        }
        if (UiMotion.isBusy(helpDrawer)) {
            return;
        }
        if (show) {
            helpVisible = true;
            double layerW = helpLayer.getWidth() > 0
                    ? helpLayer.getWidth()
                    : (shellStack != null && shellStack.getWidth() > 0 ? shellStack.getWidth() : 900);
            applyHelpDrawerWidth(layerW);
            helpLayer.setManaged(true);
            helpLayer.setVisible(true);
            if (helpBtn != null && !helpBtn.getStyleClass().contains("active")) {
                helpBtn.getStyleClass().add("active");
            }
            helpDrawer.setTranslateX(Math.max(helpDrawer.getPrefWidth(), 420));
            if (helpScrim != null) {
                helpScrim.setOpacity(0);
            }
            Platform.runLater(() -> {
                helpLayer.applyCss();
                helpLayer.layout();
                applyHelpDrawerWidth(helpLayer.getWidth());
                UiMotion.playDrawerOpen(helpScrim, helpDrawer, null);
            });
        } else {
            if (!helpLayer.isVisible()) {
                helpVisible = false;
                return;
            }
            UiMotion.playDrawerClose(helpScrim, helpDrawer, () -> {
                helpVisible = false;
                helpLayer.setVisible(false);
                helpLayer.setManaged(false);
                if (helpBtn != null) {
                    helpBtn.getStyleClass().remove("active");
                }
            });
        }
    }

    @FXML
    private void toggleSidebar() {
        if (UiMotion.isBusy(sidebar)) {
            return;
        }
        sidebarExpanded = !sidebarExpanded;
        double target = sidebarExpanded ? SIDEBAR_EXPANDED : SIDEBAR_COLLAPSED;
        UiMotion.animateWidth(sidebar, target,
                this::applySidebarContent,
                this::applySidebarContent);
    }

    /** Update icons/labels for current expanded state (width may still be animating). */
    private void applySidebarContent() {
        applySidebarExpanded(false);
    }

    private void applySidebarExpanded() {
        applySidebarExpanded(true);
    }

    private void applySidebarExpanded(boolean setWidthImmediately) {
        sidebar.getStyleClass().remove("collapsed");
        if (sidebarExpanded) {
            if (setWidthImmediately) {
                sidebar.setPrefWidth(SIDEBAR_EXPANDED);
                sidebar.setMinWidth(SIDEBAR_EXPANDED);
                sidebar.setMaxWidth(SIDEBAR_EXPANDED);
            }
            navExplorerBtn.setText("🧭 Khám phá");
            navReportsBtn.setText("📊 Báo cáo");
            runAllBtn.setText("▶️ Chạy tất cả");
            clearTooltip(navExplorerBtn);
            clearTooltip(navReportsBtn);
            clearTooltip(runAllBtn);
            clearTooltip(sidebarActiveSuite);
            if (sidebarSuiteSectionLabel != null) {
                sidebarSuiteSectionLabel.setText("📌 SUITE ĐANG MỞ");
                sidebarSuiteSectionLabel.setVisible(true);
                sidebarSuiteSectionLabel.setManaged(true);
                VBox.setMargin(sidebarSuiteSectionLabel, new Insets(10, 0, 4, 14));
            }
            if (sidebarSep != null) {
                sidebarSep.setVisible(true);
                sidebarSep.setManaged(true);
            }
            refreshActiveSuiteLabel();
            VBox.setMargin(navExplorerBtn, Insets.EMPTY);
            VBox.setMargin(navReportsBtn, Insets.EMPTY);
            if (sidebarActiveSuiteRow != null) {
                VBox.setMargin(sidebarActiveSuiteRow, new Insets(0, 10, 0, 14));
            }
            VBox.setMargin(runAllBtn, new Insets(0, 12, 8, 12));
            navExplorerBtn.setAlignment(Pos.CENTER_LEFT);
            navReportsBtn.setAlignment(Pos.CENTER_LEFT);
            runAllBtn.setAlignment(Pos.CENTER);
            sidebarActiveSuite.setAlignment(Pos.CENTER_LEFT);
            sidebarActiveSuite.setMaxWidth(Double.MAX_VALUE);
        } else {
            sidebar.getStyleClass().add("collapsed");
            if (setWidthImmediately) {
                sidebar.setPrefWidth(SIDEBAR_COLLAPSED);
                sidebar.setMinWidth(SIDEBAR_COLLAPSED);
                sidebar.setMaxWidth(SIDEBAR_COLLAPSED);
            }
            navExplorerBtn.setText("🧭");
            navReportsBtn.setText("📊");
            runAllBtn.setText("▶️");
            installTip(navExplorerBtn, "Khám phá");
            installTip(navReportsBtn, "Báo cáo");
            installTip(runAllBtn, "Chạy tất cả");
            if (sidebarSuiteSectionLabel != null) {
                sidebarSuiteSectionLabel.setVisible(false);
                sidebarSuiteSectionLabel.setManaged(false);
            }
            if (sidebarSep != null) {
                sidebarSep.setVisible(true);
                sidebarSep.setManaged(true);
            }
            String suiteTip = (activeSuiteName != null && !activeSuiteName.isBlank())
                    ? "Mở lại Workspace: " + activeSuiteName
                    : "Chưa mở suite nào";
            sidebarActiveSuite.setText(activeSuiteName != null && !activeSuiteName.isBlank() ? "📋" : "⭕");
            installTip(sidebarActiveSuite, suiteTip);
            // Symmetric gutters so emoji icons sit in the visual center of the 56px rail
            Insets iconGutter = new Insets(0, 6, 0, 6);
            VBox.setMargin(navExplorerBtn, iconGutter);
            VBox.setMargin(navReportsBtn, iconGutter);
            if (sidebarActiveSuiteRow != null) {
                VBox.setMargin(sidebarActiveSuiteRow, new Insets(8, 6, 0, 6));
            }
            VBox.setMargin(runAllBtn, new Insets(0, 6, 8, 6));
            navExplorerBtn.setAlignment(Pos.CENTER);
            navReportsBtn.setAlignment(Pos.CENTER);
            runAllBtn.setAlignment(Pos.CENTER);
            sidebarActiveSuite.setAlignment(Pos.CENTER);
            sidebarActiveSuite.setMaxWidth(Double.MAX_VALUE);
        }
        navExplorerBtn.setVisible(true);
        navExplorerBtn.setManaged(true);
        navReportsBtn.setVisible(true);
        navReportsBtn.setManaged(true);
        if (sidebarActiveSuiteRow != null) {
            sidebarActiveSuiteRow.setVisible(true);
            sidebarActiveSuiteRow.setManaged(true);
        }
        sidebarActiveSuite.setVisible(true);
        sidebarActiveSuite.setManaged(true);
        runAllBtn.setVisible(true);
        runAllBtn.setManaged(true);
        updateStopRunButton();
    }

    private void refreshActiveSuiteLabel() {
        if (sidebarActiveSuite == null) return;
        boolean hasSuite = activeSuiteName != null && !activeSuiteName.isBlank();
        if (hasSuite) {
            sidebarActiveSuite.setText("📋 " + activeSuiteName);
            installTip(sidebarActiveSuite, "Click để mở lại Workspace: " + activeSuiteName);
            if (!sidebarActiveSuite.getStyleClass().contains("has-suite")) {
                sidebarActiveSuite.getStyleClass().add("has-suite");
            }
        } else {
            sidebarActiveSuite.setText("⭕ (trống)");
            installTip(sidebarActiveSuite, "Chưa mở suite nào từ Khám phá");
            sidebarActiveSuite.getStyleClass().remove("has-suite");
        }
        if (clearActiveSuiteBtn != null) {
            boolean showClear = hasSuite && sidebarExpanded;
            clearActiveSuiteBtn.setVisible(showClear);
            clearActiveSuiteBtn.setManaged(showClear);
        }
        if (activeSuiteLabel != null) {
            if (hasSuite) {
                activeSuiteLabel.setText("📋 Suite: " + activeSuiteName);
                installTip(activeSuiteLabel, "Click để mở lại Workspace");
            } else {
                activeSuiteLabel.setText("");
                clearTooltip(activeSuiteLabel);
            }
        }
    }

    public String getActiveSuiteName() {
        return activeSuiteName;
    }

    public Path getActiveSuiteDir() {
        return activeSuiteDir;
    }

    /** Remember the Workspace suite so sidebar stays sticky when navigating away. */
    public void setActiveWorkspace(String suiteName, Path suiteDir) {
        activeSuiteName = (suiteName != null && !suiteName.isBlank()) ? suiteName.trim() : null;
        activeSuiteDir = suiteDir;
        if (sidebarExpanded) {
            refreshActiveSuiteLabel();
        } else {
            applySidebarExpanded(false);
        }
    }

    private static void installTip(javafx.scene.Node node, String text) {
        if (node == null) return;
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(200));
        Tooltip.install(node, tip);
        if (node instanceof Button btn) {
            btn.setTooltip(tip);
        } else if (node instanceof Label label) {
            label.setTooltip(tip);
        }
    }

    private static void clearTooltip(javafx.scene.Node node) {
        if (node == null) return;
        if (node instanceof Button btn) {
            Tooltip existing = btn.getTooltip();
            if (existing != null) {
                Tooltip.uninstall(node, existing);
            }
            btn.setTooltip(null);
        } else if (node instanceof Label label) {
            Tooltip existing = label.getTooltip();
            if (existing != null) {
                Tooltip.uninstall(node, existing);
            }
            label.setTooltip(null);
        }
    }

    @FXML
    private void showExplorer() {
        hideHelp();
        ViewNavigator.navigateTo(ViewNavigator.Page.EXPLORER);
        setActiveNavButton(navExplorerBtn);
    }

    @FXML
    private void showReports() {
        hideHelp();
        ViewNavigator.navigateTo(ViewNavigator.Page.REPORTS);
        setActiveNavButton(navReportsBtn);
    }

    @FXML
    private void stopRun() {
        if (!RunCancelService.isRunActive()) {
            return;
        }
        RunCancelService.requestCancel();
        StatusBarService.set("Đang dừng…");
        ToastService.info("⏹ Đang dừng chạy test…");
        updateStopRunButton();
    }

    private void updateStopRunButton() {
        if (stopRunBtn == null) return;
        boolean active = RunCancelService.isRunActive();
        stopRunBtn.setVisible(active);
        stopRunBtn.setManaged(active);
        stopRunBtn.setDisable(!active || RunCancelService.isCancelRequested());
    }

    @FXML
    private void runAll() {
        ExplorerPageController explorerCtrl =
                ViewNavigator.getController(ViewNavigator.Page.EXPLORER, ExplorerPageController.class);
        if (explorerCtrl == null) {
            ToastService.warn("⚠️ Chưa tải được trang Khám phá");
            return;
        }
        StatusBarService.set("Đang chạy tất cả…");
        BusyButtons.runAsync(
                runAllBtn,
                sidebarExpanded ? "Đang chạy…" : "…",
                () -> {
                    explorerCtrl.runAllBlocking();
                    return null;
                },
                ignored -> {
                    StatusBarService.ready();
                    applySidebarExpanded();
                },
                ex -> {
                    StatusBarService.ready();
                    applySidebarExpanded();
                    ToastService.error("❌ Chạy tất cả thất bại: " + ex.getMessage());
                }
        );
    }

    public void updateStatusBar(String message) {
        StatusBarService.set(message);
    }

    public void setBrowserStatus(String browserName) {
        if (browserLabel != null && browserName != null && !browserName.isBlank()) {
            browserLabel.setText("🌐 " + browserName.replace("🌐 ", ""));
        }
    }

    private void refreshBrowserLabel() {
        try {
            SettingsStore.Settings settings = new SettingsStore().load();
            if (settings.getBrowser() != null) {
                setBrowserStatus(settings.getBrowser());
            }
        } catch (IOException ignored) {
            setBrowserStatus("Chromium");
        }
    }

    private void setActiveNavButton(Button active) {
        navExplorerBtn.getStyleClass().remove("active");
        navReportsBtn.getStyleClass().remove("active");
        if (!active.getStyleClass().contains("active")) {
            active.getStyleClass().add("active");
        }
    }
}
