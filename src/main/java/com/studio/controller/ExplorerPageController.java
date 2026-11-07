package com.studio.controller;

import com.studio.model.*;
import com.studio.service.RunProgressService;
import com.studio.service.StatusBarService;
import com.studio.service.TestRunnerService;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.util.BusyButtons;
import com.studio.util.DialogTheme;
import com.studio.util.EntityTime;
import com.studio.util.RunTargetResolve;
import com.studio.util.StatusDisplay;
import com.studio.util.StudioIcons;
import com.studio.util.UiMotion;
import com.studio.util.StudioPaths;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Controls the project tree view with Group→Suite→Case hierarchy.
 * Loaded from explorer_page.fxml via fx:controller.
 */
public class ExplorerPageController {

    // ── FXML-injected controls (matched exactly to fx:id values in explorer_page.fxml) ──

    @FXML private TreeView<Object> projectTree;
    @FXML private Button addGroupBtn;
    @FXML private Button addSuiteBtn;
    @FXML private Button addCaseBtn;
    @FXML private Separator createToolbarSep;
    @FXML private Button copyBtn;
    @FXML private Button openWorkspaceBtn;
    @FXML private Button runNodeBtn;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField searchField;
    @FXML private Button chipAll;
    @FXML private Button chipActive;
    @FXML private Button chipDraft;
    @FXML private Button chipInactive;
    @FXML private Label previewName;
    @FXML private Label previewType;
    @FXML private Label previewStatus;
    @FXML private Label previewDesc;
    @FXML private Label previewStats;
    @FXML private VBox previewViewBox;
    @FXML private Label previewViewDesc;
    @FXML private Label previewViewStatus;
    @FXML private Label previewViewUrl;
    @FXML private Label previewViewBrowser;
    @FXML private Label previewViewHeadless;
    @FXML private Label previewCreatedLabel;
    @FXML private Label previewUpdatedLabel;
    @FXML private Button previewEditBtn;

    // ── Services & stores ──

    private final ProjectStore projectStore = new ProjectStore();
    private final ReportStore reportStore = new ReportStore();

    private TestGroup rootGroup;
    private Path rootProjectDir;
    // JavaFX 26: TreeItem.setUserData/getUserData removed; use a map instead
    private final Map<TreeItem<Object>, NodeRef> nodeRefs = new HashMap<>();
    /** Row under context menu (right-click); does not change TreeView selection. */
    private TreeItem<Object> contextTarget;

    private NodeRef getNodeRef(TreeItem<Object> item) {
        return nodeRefs.get(item);
    }

    private void setNodeRef(TreeItem<Object> item, NodeRef ref) {
        nodeRefs.put(item, ref);
    }
    private final ObservableList<String> filterOptions = FXCollections.observableArrayList(
            StatusDisplay.ALL_LABEL,
            StatusDisplay.label("ACTIVE"),
            StatusDisplay.label("DRAFT"),
            StatusDisplay.label("INACTIVE")
    );

    // ── Inner class for tree node metadata ──

    private static class NodeRef {
        final String type; // "group", "suite", "case"
        final String name;
        final String status;
        final String parentName;
        final String suitePath;    // absolute suite directory
        final String caseId;
        final String dirPath;      // absolute group/suite directory (group/suite nodes)

        NodeRef(String type, String name, String status, String parentName,
                String suitePath, String caseId, String dirPath) {
            this.type = type;
            this.name = name;
            this.status = status;
            this.parentName = parentName;
            this.suitePath = suitePath;
            this.caseId = caseId;
            this.dirPath = dirPath;
        }
    }

    // ── Initialize ──

    @FXML
    public void initialize() {
        rootProjectDir = StudioPaths.getProjectDir();
        loadTree();
        setupTreeView();
        setupToolbar();
        setupStatusFilter();
        setupSearchFilter();
        setupDragDrop();
        RunProgressService.addListener(() -> {
            if (projectTree != null) {
                projectTree.refresh();
            }
        });
    }

    // ── Tree loading & building ──

    private void loadTree() {
        try {
            rootGroup = projectStore.loadTree();
        } catch (IOException e) {
            rootGroup = new TestGroup("Project", "Root project group");
            rootGroup.setType("group");
            rootGroup.setChildren(new ArrayList<>());
            rootGroup.setSuites(new ArrayList<>());
            ToastService.warn("⚠️ Không thể tải cây: " + e.getMessage());
        }
        buildTree(rootGroup);
    }

    private static String resolveRunOverlay(NodeRef ref) {
        if (ref == null || !RunProgressService.hasOverlay()) {
            return null;
        }
        return switch (ref.type) {
            case "case" -> RunProgressService.overlayForCase(ref.caseId, ref.parentName, ref.name);
            case "suite" -> RunProgressService.overlayForSuiteDir(
                    ref.suitePath != null && !ref.suitePath.isBlank() ? ref.suitePath : ref.dirPath);
            case "group" -> RunProgressService.overlayForGroupDir(ref.dirPath);
            default -> null;
        };
    }

    private void buildTree(TestGroup group) {
        nodeRefs.clear();
        // Hidden synthetic root — children appear as top-level; preview uses root when nothing selected
        TreeItem<Object> rootItem = new TreeItem<>("📦 Dự án");
        setNodeRef(rootItem, new NodeRef("group", "Dự án", null,
                null, null, null, rootProjectDir.toString()));
        projectTree.setRoot(rootItem);
        projectTree.setShowRoot(false);
        rootItem.setExpanded(true);
        buildChildren(rootItem, group, rootProjectDir);
        projectTree.getSelectionModel().clearSelection();
        showRootPreview();
    }

    private void buildChildren(TreeItem<Object> parent, TestGroup group, Path groupDir) {
        if (group.getChildren() != null) {
            for (TestGroup child : group.getChildren()) {
                if ("suite".equalsIgnoreCase(child.getType())) {
                    // Legacy wrapper — skip; suites come from getSuites()
                    continue;
                }
                Path childDir = groupDir.resolve(child.getName());
                TreeItem<Object> item = new TreeItem<>("📁 " + child.getName());
                setNodeRef(item, new NodeRef("group", child.getName(), null,
                        group.getName(), null, null, childDir.toString()));
                buildChildren(item, child, childDir);
                item.setExpanded(true);
                parent.getChildren().add(item);
            }
        }
        if (group.getSuites() != null) {
            for (TestSuite suite : group.getSuites()) {
                Path suiteDir = groupDir.resolve(suite.getName());
                String suitePath = Files.isDirectory(suiteDir) ? suiteDir.toString() : "";
                TreeItem<Object> item = new TreeItem<>("📋 " + suite.getName());
                setNodeRef(item, new NodeRef("suite", suite.getName(),
                        suite.getStatus(), group.getName(), suitePath, null, suitePath));
                if (suite.getCases() != null) {
                    for (TestCase tc : suite.getCases()) {
                        TreeItem<Object> caseItem = new TreeItem<>(StudioIcons.CASE + " " + tc.getName());
                        setNodeRef(caseItem, new NodeRef("case", tc.getName(),
                                tc.getStatus(), suite.getName(), suitePath, tc.getId(), suitePath));
                        item.getChildren().add(caseItem);
                    }
                }
                item.setExpanded(true);
                parent.getChildren().add(item);
            }
        }
    }

    // ── Tree view setup ──

    private void setupTreeView() {
        projectTree.setShowRoot(false);
        ContextMenu treeMenu = buildTreeContextMenu();

        projectTree.setCellFactory(tv -> {
            TreeCell<Object> cell = new TreeCell<>() {
                private final HBox row = new HBox(6);
                private final Label nameLabel = new Label();
                private final HBox actionsBox = new HBox(3);
                private final Button runBtn = new Button("▶");
                private final Button openBtn = new Button("📂");
                private final Button addBtn = new Button("+");
                private final Button editBtn = new Button("✏");
                private final Button copyBtn = new Button("📋");
                private final Label statusLabel = new Label();

                {
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("tree-row");
                    nameLabel.getStyleClass().add("tree-row-name");
                    nameLabel.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);

                    actionsBox.setAlignment(Pos.CENTER_RIGHT);
                    actionsBox.getStyleClass().add("tree-cell-actions");
                    actionsBox.setVisible(false);
                    actionsBox.setManaged(false);

                    for (Button btn : List.of(runBtn, openBtn, addBtn, editBtn, copyBtn)) {
                        btn.getStyleClass().add("tree-action-btn");
                        btn.setFocusTraversable(false);
                        btn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, javafx.event.Event::consume);
                        btn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
                    }
                    runBtn.getStyleClass().add("tree-action-run");
                    runBtn.setTooltip(new Tooltip("Chạy ngay"));
                    openBtn.setTooltip(new Tooltip("Mở trong Workspace"));
                    addBtn.setTooltip(new Tooltip("Thêm mục mới"));
                    editBtn.setTooltip(new Tooltip("Chỉnh sửa"));
                    copyBtn.setTooltip(new Tooltip("Sao chép"));

                    actionsBox.getChildren().addAll(runBtn, openBtn, addBtn, editBtn, copyBtn);

                    statusLabel.getStyleClass().add("tree-row-status");
                    statusLabel.setMinWidth(Label.USE_PREF_SIZE);
                    row.getChildren().addAll(nameLabel, actionsBox, statusLabel);

                    hoverProperty().addListener((obs, wasHovered, isHovered) -> {
                        boolean show = isHovered && !isEmpty() && getItem() != null;
                        actionsBox.setVisible(show);
                        actionsBox.setManaged(show);
                    });
                }

                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("tree-group", "tree-suite", "tree-case",
                            "status-active", "status-draft", "status-inactive", "status-ready",
                            "status-passed", "status-failed", "status-skipped", "status-error",
                            "status-pending", "status-running", "status-unknown");
                    statusLabel.getStyleClass().removeAll(
                            "status-active", "status-draft", "status-inactive", "status-ready",
                            "status-passed", "status-failed", "status-skipped", "status-error",
                            "status-pending", "status-running", "status-unknown");
                    UiMotion.stopPulse(statusLabel);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        actionsBox.setVisible(false);
                        actionsBox.setManaged(false);
                        return;
                    }
                    nameLabel.setText(String.valueOf(item));
                    TreeItem<Object> treeItem = getTreeItem();
                    NodeRef ref = treeItem != null ? getNodeRef(treeItem) : null;
                    String statusText = "";
                    if (ref != null) {
                        switch (ref.type) {
                            case "group" -> getStyleClass().add("tree-group");
                            case "suite" -> getStyleClass().add("tree-suite");
                            case "case" -> getStyleClass().add("tree-case");
                            default -> { }
                        }
                        String overlay = resolveRunOverlay(ref);
                        String displayStatus = overlay != null ? overlay : ref.status;
                        if (displayStatus != null && !displayStatus.isBlank()) {
                            statusText = StatusDisplay.label(displayStatus);
                            statusLabel.getStyleClass().add(StatusDisplay.styleClass(displayStatus));
                            if ("RUNNING".equalsIgnoreCase(displayStatus)) {
                                UiMotion.startPulse(statusLabel);
                            }
                        }

                        // Wire inline actions
                        boolean isGroup = "group".equals(ref.type);
                        boolean isSuite = "suite".equals(ref.type);
                        boolean isCase = "case".equals(ref.type);
                        boolean isRoot = isRootItem(treeItem);

                        runBtn.setVisible(true);
                        runBtn.setManaged(true);
                        runBtn.setOnAction(e -> {
                            contextTarget = treeItem;
                            projectTree.getSelectionModel().select(treeItem);
                            runSelected();
                        });

                        openBtn.setVisible(isSuite || isCase);
                        openBtn.setManaged(isSuite || isCase);
                        openBtn.setOnAction(e -> openWorkspaceForRef(ref));

                        addBtn.setVisible(isGroup || isSuite);
                        addBtn.setManaged(isGroup || isSuite);
                        addBtn.setTooltip(new Tooltip(isGroup ? "Thêm Suite" : "Thêm Case"));
                        addBtn.setOnAction(e -> {
                            contextTarget = treeItem;
                            projectTree.getSelectionModel().select(treeItem);
                            if (isGroup) {
                                addSuite();
                            } else if (isSuite) {
                                addCase();
                            }
                        });

                        editBtn.setVisible(!isRoot);
                        editBtn.setManaged(!isRoot);
                        editBtn.setOnAction(e -> {
                            contextTarget = treeItem;
                            projectTree.getSelectionModel().select(treeItem);
                            editSelectedEntity();
                        });

                        copyBtn.setVisible(isSuite || isCase);
                        copyBtn.setManaged(isSuite || isCase);
                        copyBtn.setOnAction(e -> {
                            contextTarget = treeItem;
                            projectTree.getSelectionModel().select(treeItem);
                            copyItem();
                        });
                    }
                    statusLabel.setText(statusText);
                    statusLabel.setVisible(!statusText.isEmpty());
                    statusLabel.setManaged(!statusText.isEmpty());
                    setText(null);
                    setGraphic(row);
                }
            };
            // Primary: click again on selected row → unselect. Secondary: menu without changing selection.
            cell.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                if (cell.isEmpty() || cell.getTreeItem() == null) {
                    return;
                }
                TreeItem<Object> item = cell.getTreeItem();
                if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    contextTarget = item;
                    showContextMenu(treeMenu, cell, e.getScreenX(), e.getScreenY());
                    e.consume();
                    return;
                }
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && e.getClickCount() == 1
                        && !e.isShiftDown() && !e.isControlDown() && !e.isMetaDown()
                        && item.equals(projectTree.getSelectionModel().getSelectedItem())) {
                    contextTarget = null;
                    javafx.application.Platform.runLater(() -> {
                        projectTree.getSelectionModel().clearSelection();
                        showRootPreview();
                    });
                    e.consume();
                }
            });
            cell.setOnMouseClicked(e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY || e.getClickCount() != 2) {
                    return;
                }
                TreeItem<Object> item = cell.getTreeItem();
                if (item == null) return;
                contextTarget = null;
                projectTree.getSelectionModel().select(item);
                NodeRef ref = getNodeRef(item);
                if (ref == null) return;
                if ("suite".equals(ref.type) || "case".equals(ref.type)) {
                    openWorkspaceForRef(ref);
                    e.consume();
                }
            });
            return cell;
        });

        projectTree.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.SECONDARY || e.isConsumed()) {
                return;
            }
            if (findAncestor(e.getTarget(), TreeCell.class) != null) {
                return;
            }
            // Empty area: open menu without changing current selection
            contextTarget = null;
            showContextMenu(treeMenu, projectTree, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        treeMenu.setOnHidden(ev -> contextTarget = null);

        projectTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, selectedItem) -> {
            if (selectedItem != null) {
                updatePreview(selectedItem);
            } else {
                showRootPreview();
            }
        });
    }

    /** Preview for the hidden root (no tree row selected). */
    private void showRootPreview() {
        TreeItem<Object> root = projectTree.getRoot();
        if (root != null) {
            updatePreview(root);
        } else {
            clearPreview();
        }
    }

    private static <T> T findAncestor(Object target, Class<T> type) {
        if (!(target instanceof javafx.scene.Node node)) {
            return null;
        }
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (type.isInstance(cur)) {
                return type.cast(cur);
            }
            cur = cur.getParent();
        }
        return null;
    }

    private static void showContextMenu(ContextMenu menu, javafx.scene.Node anchor,
                                        double screenX, double screenY) {
        if (menu == null || anchor == null) return;
        if (menu.isShowing()) {
            menu.hide();
        }
        if (anchor.getScene() != null && anchor.getScene().getWindow() != null) {
            menu.show(anchor.getScene().getWindow(), screenX, screenY);
        } else {
            menu.show(anchor, screenX, screenY);
        }
    }

    private boolean isRootItem(TreeItem<Object> item) {
        return item != null && item == projectTree.getRoot();
    }

    /** Context-menu row if present; otherwise current selection. */
    private TreeItem<Object> resolveTarget() {
        return contextTarget != null ? contextTarget : projectTree.getSelectionModel().getSelectedItem();
    }

    private ContextMenu buildTreeContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("studio-context-menu");
        menu.setOnShowing(ev -> {
            menu.getItems().clear();
            TreeItem<Object> selected = resolveTarget();
            NodeRef ref = selected != null ? getNodeRef(selected) : null;
            if (ref == null) {
                menu.getItems().addAll(
                        menuItem("📁 Nhóm mới", this::addGroup),
                        menuItem("📋 Suite mới", this::addSuite),
                        new SeparatorMenuItem(),
                        runMenuItem("▶ Chạy tất cả", this::runSelected)
                );
                return;
            }
            switch (ref.type) {
                case "group" -> {
                    menu.getItems().addAll(
                            menuItem("📁 Nhóm mới", this::addGroup),
                            menuItem("📋 Suite mới", this::addSuite)
                    );
                    if (!isRootItem(selected)) {
                        menu.getItems().addAll(
                                new SeparatorMenuItem(),
                                menuItem(StudioIcons.EDIT + " Sửa", this::editSelectedEntity),
                                menuItem("🗑️ Xóa", this::deleteItem),
                                new SeparatorMenuItem(),
                                runMenuItem("▶ Chạy nhóm", this::runSelected)
                        );
                    } else {
                        menu.getItems().addAll(
                                new SeparatorMenuItem(),
                                runMenuItem("▶ Chạy tất cả", this::runSelected)
                        );
                    }
                }
                case "suite" -> {
                    menu.getItems().addAll(
                            menuItem(StudioIcons.CASE + " Case mới", this::addCase),
                            workspaceMenuItem("📂 Mở Workspace", this::openWorkspace),
                            new SeparatorMenuItem()
                    );
                    String suiteKey = StatusDisplay.normalize(ref.status);
                    if ("DRAFT".equals(suiteKey) || suiteKey.isEmpty()) {
                        menu.getItems().add(menuItem(
                                "🚀 Publish",
                                () -> changeSuiteStatus("ACTIVE")));
                    } else if ("ACTIVE".equals(suiteKey)) {
                        menu.getItems().add(menuItem(
                                "⏸️ Vô hiệu hóa",
                                () -> changeSuiteStatus("INACTIVE")));
                    } else if ("INACTIVE".equals(suiteKey)) {
                        menu.getItems().add(menuItem(
                                "📝 Đưa về Bản nháp",
                                () -> changeSuiteStatus("DRAFT")));
                    }
                    menu.getItems().addAll(
                            new SeparatorMenuItem(),
                            menuItem(StudioIcons.EDIT + " Sửa", this::editSelectedEntity),
                            menuItem("📄 Sao chép", this::copyItem),
                            menuItem("🗑️ Xóa", this::deleteItem)
                        );
                    if (StatusDisplay.isActive(ref.status)) {
                        menu.getItems().addAll(
                                new SeparatorMenuItem(),
                                runMenuItem("▶ Chạy suite", this::runSelected)
                        );
                    }
                }
                case "case" -> {
                    menu.getItems().addAll(
                            workspaceMenuItem("📂 Mở Workspace", this::openWorkspace),
                            new SeparatorMenuItem(),
                            menuItem(StudioIcons.EDIT + " Sửa", this::editSelectedEntity),
                            menuItem("📄 Sao chép", this::copyItem),
                            menuItem("🗑️ Xóa", this::deleteItem)
                    );
                    TreeItem<Object> parent = selected.getParent();
                    NodeRef suiteRef = parent != null ? getNodeRef(parent) : null;
                    String suiteSt = suiteRef != null ? suiteRef.status : null;
                    if (StatusDisplay.canRunCase(suiteSt, ref.status)) {
                        menu.getItems().addAll(
                                new SeparatorMenuItem(),
                                runMenuItem("▶ Chạy case", this::runSelected)
                        );
                    }
                }
                default -> menu.getItems().add(menuItem("📁 Nhóm mới", this::addGroup));
            }
        });
        return menu;
    }

    private MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(ev -> action.run());
        return item;
    }

    private MenuItem workspaceMenuItem(String text, Runnable action) {
        MenuItem item = menuItem(text, action);
        item.getStyleClass().add("ctx-cta-workspace");
        return item;
    }

    private MenuItem runMenuItem(String text, Runnable action) {
        MenuItem item = menuItem(text, action);
        item.getStyleClass().add("ctx-cta-run");
        return item;
    }

    // ── Preview panel ──

    private void clearPreview() {
        previewName.setText("👆 Chọn một mục");
        previewType.setText("");
        previewStatus.setText("");
        previewStatus.getStyleClass().removeAll(
                "status-active", "status-draft", "status-inactive", "status-ready",
                "status-passed", "status-failed", "status-skipped", "status-error",
                "status-pending", "status-unknown");
        previewDesc.setText("");
        previewStats.setText("");
        if (previewViewBox != null) {
            previewViewBox.setVisible(false);
            previewViewBox.setManaged(false);
        }
        openWorkspaceBtn.setVisible(false);
        runNodeBtn.setVisible(false);
        // Empty / unknown → treat as project root: create group + suite
        syncCreateToolbar(true, true, false);
        copyBtn.setDisable(true);
        copyBtn.setVisible(false);
        copyBtn.setManaged(false);
    }

    private void updatePreview(TreeItem<Object> item) {
        NodeRef ref = getNodeRef(item);
        if (ref == null) {
            clearPreview();
            return;
        }

        if (previewViewBox != null) {
            previewViewBox.setVisible(false);
            previewViewBox.setManaged(false);
        }

        if ("group".equals(ref.type)) {
            previewStatus.setVisible(false);
            previewStatus.setManaged(false);
            if (isRootItem(item)) {
                previewName.setText("Dự án");
                previewType.setText("📦 Gốc dự án");
                previewDesc.setText("Tổng quan toàn bộ Nhóm / Suite / Case trong dự án. "
                        + "Dùng sidebar «Chạy tất cả» hoặc chuột phải vùng trống trên cây. "
                        + "Thêm Nhóm / Suite từ toolbar.");
            } else {
                previewName.setText(ref.name);
                previewType.setText("📁 Nhóm");
                previewDesc.setText("");
                loadGroupPreviewView(ref);
            }
            int groupCount = countGroupsUnderItem(item);
            int suiteCount = countSuitesUnderItem(item);
            int caseCount = countCasesUnderItem(item);
            previewStats.setText(String.format(
                    isRootItem(item)
                            ? "Nhóm: %d | Suite: %d | Case: %d"
                            : "Nhóm con: %d | Suite: %d | Case: %d",
                    groupCount, suiteCount, caseCount));
        } else if ("suite".equals(ref.type)) {
            previewName.setText(ref.name);
            previewType.setText("📋 Suite");
            previewStatus.setVisible(false);
            previewStatus.setManaged(false);
            previewDesc.setText("");
            int caseCount = countCasesInItem(item);
            previewStats.setText(String.format("Case: %d", caseCount));
            loadSuitePreviewView(ref);
        } else if ("case".equals(ref.type)) {
            previewName.setText(ref.name);
            previewType.setText(StudioIcons.CASE + " Case");
            previewStatus.setVisible(false);
            previewStatus.setManaged(false);
            previewDesc.setText("");
            previewStats.setText("Suite: " + ref.parentName);
            loadCasePreviewView(ref);
        }

        boolean isGroup = "group".equals(ref.type);
        boolean isSuite = "suite".equals(ref.type);
        boolean isCase = "case".equals(ref.type);
        boolean root = isRootItem(item);
        openWorkspaceBtn.setVisible(isSuite || isCase);
        boolean canRunNode = false;
        if (root) {
            // Sidebar «Chạy tất cả» already covers this — no duplicate on preview
            canRunNode = false;
        } else if (isGroup) {
            canRunNode = true;
            runNodeBtn.setText("▶ Chạy nhóm");
        } else if (isSuite) {
            canRunNode = StatusDisplay.isActive(ref.status);
            runNodeBtn.setText("▶ Chạy Suite");
        } else if (isCase) {
            TreeItem<Object> parent = item.getParent();
            NodeRef suiteRef = parent != null ? getNodeRef(parent) : null;
            canRunNode = StatusDisplay.canRunCase(
                    suiteRef != null ? suiteRef.status : null, ref.status);
            runNodeBtn.setText("▶ Chạy Case");
        }
        runNodeBtn.setVisible(canRunNode);
        if (runNodeBtn != null) {
            runNodeBtn.setManaged(canRunNode);
        }

        // Toolbar create buttons by selection:
        // group/root → Nhóm + Suite; suite → Case; case → none
        boolean emptySelection = projectTree.getSelectionModel().getSelectedItem() == null;
        boolean asGroup = emptySelection || isGroup || root;
        boolean asSuite = !emptySelection && isSuite;
        boolean asCase = !emptySelection && isCase;
        syncCreateToolbar(asGroup, asGroup, asSuite);
        boolean showCopy = asSuite || asCase;
        copyBtn.setVisible(showCopy);
        copyBtn.setManaged(showCopy);
        copyBtn.setDisable(!showCopy);
    }

    /** Show only create actions that apply to the current selection. */
    private void syncCreateToolbar(boolean showGroup, boolean showSuite, boolean showCase) {
        setToolbarActionVisible(addGroupBtn, showGroup);
        setToolbarActionVisible(addSuiteBtn, showSuite);
        setToolbarActionVisible(addCaseBtn, showCase);
        boolean anyCreate = showGroup || showSuite || showCase;
        if (createToolbarSep != null) {
            createToolbarSep.setVisible(anyCreate);
            createToolbarSep.setManaged(anyCreate);
        }
    }

    private static void setToolbarActionVisible(Button button, boolean visible) {
        if (button == null) return;
        button.setVisible(visible);
        button.setManaged(visible);
        button.setDisable(!visible);
    }

    private void showPreviewViewBox() {
        if (previewViewBox == null) return;
        previewViewBox.setVisible(true);
        previewViewBox.setManaged(true);
        if (previewEditBtn != null) {
            previewEditBtn.setVisible(true);
            previewEditBtn.setManaged(true);
        }
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String browserDisplay(String browser) {
        if (browser == null || browser.isBlank() || RunTargetResolve.isDefaultBrowser(browser)) {
            return "🖥️ " + RunTargetResolve.BROWSER_DEFAULT_LABEL;
        }
        return "🖥️ " + browser;
    }

    private static String headlessDisplay(Boolean headless) {
        if (headless == null) {
            return "🙈 Ẩn trình duyệt: theo mặc định";
        }
        return headless ? "🙈 Ẩn trình duyệt: Có" : "🙈 Ẩn trình duyệt: Không";
    }

    private void loadGroupPreviewView(NodeRef ref) {
        Path groupDir = resolveGroupDir(ref);
        if (groupDir == null || previewViewBox == null) {
            previewDesc.setText("—");
            return;
        }
        try {
            TestGroup group = projectStore.loadGroup(groupDir);
            String description = blankAsDash(group.getDescription());
            // Detail under «Thông tin» only — avoid duplicating above the stats
            previewDesc.setText("");
            previewViewDesc.setText(description);
            previewViewStatus.setVisible(false);
            previewViewStatus.setManaged(false);
            previewViewUrl.setVisible(false);
            previewViewUrl.setManaged(false);
            previewViewBrowser.setVisible(false);
            previewViewBrowser.setManaged(false);
            previewViewHeadless.setVisible(false);
            previewViewHeadless.setManaged(false);
            previewCreatedLabel.setText("");
            previewCreatedLabel.setVisible(false);
            previewCreatedLabel.setManaged(false);
            previewUpdatedLabel.setText("");
            previewUpdatedLabel.setVisible(false);
            previewUpdatedLabel.setManaged(false);
            showPreviewViewBox();
        } catch (IOException e) {
            previewDesc.setText("—");
            ToastService.error("❌ Không đọc được nhóm: " + e.getMessage());
        }
    }

    private void loadSuitePreviewView(NodeRef ref) {
        Path suiteDir = resolveSuitePath(ref);
        if (suiteDir == null || previewViewBox == null) return;
        try {
            TestSuite suite = projectStore.loadAndMigrateSuite(suiteDir);
            previewViewDesc.setText(blankAsDash(suite.getDescription()));
            previewViewStatus.setText("Trạng thái: " + StatusDisplay.label(suite.getStatus()));
            previewViewStatus.setVisible(true);
            previewViewStatus.setManaged(true);
            previewViewUrl.setVisible(false);
            previewViewUrl.setManaged(false);
            Boolean hl = suite.getHeadless();
            previewViewHeadless.setText(headlessDisplay(hl));
            previewViewHeadless.setVisible(true);
            previewViewHeadless.setManaged(true);
            boolean showBrowser = !Boolean.TRUE.equals(hl);
            previewViewBrowser.setText(browserDisplay(suite.getBrowser()));
            previewViewBrowser.setVisible(showBrowser);
            previewViewBrowser.setManaged(showBrowser);
            previewCreatedLabel.setText("Tạo: " + EntityTime.formatDisplay(suite.getCreatedAt()));
            previewCreatedLabel.setVisible(true);
            previewCreatedLabel.setManaged(true);
            previewUpdatedLabel.setText("Sửa: " + EntityTime.formatDisplay(suite.getUpdatedAt()));
            previewUpdatedLabel.setVisible(true);
            previewUpdatedLabel.setManaged(true);
            showPreviewViewBox();
        } catch (IOException e) {
            ToastService.error("❌ Không đọc được suite: " + e.getMessage());
        }
    }

    private void loadCasePreviewView(NodeRef ref) {
        Path suiteDir = resolveSuitePath(ref);
        if (suiteDir == null || ref.caseId == null || previewViewBox == null) return;
        try {
            TestSuite suite = projectStore.loadAndMigrateSuite(suiteDir);
            TestCase tc = suite.getCases().stream()
                    .filter(c -> ref.caseId.equals(c.getId()))
                    .findFirst()
                    .orElse(null);
            if (tc == null) return;
            previewViewDesc.setText(blankAsDash(tc.getDescription()));
            previewViewStatus.setText("Trạng thái: " + StatusDisplay.label(tc.getStatus()));
            previewViewStatus.setVisible(true);
            previewViewStatus.setManaged(true);
            String url = tc.getStartUrl() != null ? tc.getStartUrl().trim() : "";
            previewViewUrl.setText("URL: " + (url.isEmpty() ? "—" : url));
            previewViewUrl.setVisible(true);
            previewViewUrl.setManaged(true);
            Boolean hl = tc.getHeadless();
            if (hl == null) {
                hl = suite.getHeadless();
            }
            previewViewHeadless.setText(headlessDisplay(tc.getHeadless() == null ? null : hl));
            previewViewHeadless.setVisible(true);
            previewViewHeadless.setManaged(true);
            boolean showBrowser = !Boolean.TRUE.equals(hl);
            String browser = tc.getBrowser();
            if (browser == null || browser.isBlank()) {
                browser = suite.getBrowser();
            }
            previewViewBrowser.setText(browserDisplay(browser));
            previewViewBrowser.setVisible(showBrowser);
            previewViewBrowser.setManaged(showBrowser);
            previewCreatedLabel.setText("Tạo: " + EntityTime.formatDisplay(tc.getCreatedAt()));
            previewCreatedLabel.setVisible(true);
            previewCreatedLabel.setManaged(true);
            previewUpdatedLabel.setText("Sửa: " + EntityTime.formatDisplay(tc.getUpdatedAt()));
            previewUpdatedLabel.setVisible(true);
            previewUpdatedLabel.setManaged(true);
            showPreviewViewBox();
        } catch (IOException e) {
            ToastService.error("❌ Không đọc được case: " + e.getMessage());
        }
    }

    private int countGroupsUnderItem(TreeItem<Object> item) {
        int count = 0;
        for (TreeItem<Object> child : item.getChildren()) {
            NodeRef ref = getNodeRef(child);
            if (ref != null && "group".equals(ref.type)) count++;
            count += countGroupsUnderItem(child);
        }
        return count;
    }

    private int countSuitesUnderItem(TreeItem<Object> item) {
        int count = 0;
        for (TreeItem<Object> child : item.getChildren()) {
            NodeRef ref = getNodeRef(child);
            if (ref != null && "suite".equals(ref.type)) count++;
            count += countSuitesUnderItem(child);
        }
        return count;
    }

    private int countCasesUnderItem(TreeItem<Object> item) {
        int count = 0;
        for (TreeItem<Object> child : item.getChildren()) {
            NodeRef ref = getNodeRef(child);
            if (ref != null && "case".equals(ref.type)) count++;
            count += countCasesUnderItem(child);
        }
        return count;
    }

    private int countCasesInItem(TreeItem<Object> item) {
        return (int) item.getChildren().stream()
                .filter(c -> getNodeRef(c) != null
                        && "case".equals(getNodeRef(c).type))
                .count();
    }

    // ── Toolbar setup ──

    private void setupToolbar() {
        addGroupBtn.setOnAction(e -> addGroup());
        addSuiteBtn.setOnAction(e -> addSuite());
        if (addCaseBtn != null) {
            addCaseBtn.setOnAction(e -> addCase());
        }
        copyBtn.setOnAction(e -> copyItem());
        openWorkspaceBtn.setOnAction(e -> openWorkspace());
        runNodeBtn.setOnAction(e -> runNode());
        syncCreateToolbar(true, true, false);
        copyBtn.setVisible(false);
        copyBtn.setManaged(false);
        copyBtn.setDisable(true);
    }

    // ── Status filter ──

    private void setupStatusFilter() {
        statusFilter.getItems().addAll(filterOptions);
        statusFilter.setValue(StatusDisplay.ALL_LABEL);
        statusFilter.setOnAction(e -> {
            syncFilterChipsWithCombo(statusFilter.getValue());
            refreshTree();
        });
    }

    @FXML
    public void filterChipAll() {
        setFilterChipActive(chipAll);
        if (statusFilter != null) {
            statusFilter.setValue(StatusDisplay.ALL_LABEL);
        }
        refreshTree();
    }

    @FXML
    public void filterChipActive() {
        setFilterChipActive(chipActive);
        if (statusFilter != null) {
            statusFilter.setValue(StatusDisplay.label("ACTIVE"));
        }
        refreshTree();
    }

    @FXML
    public void filterChipDraft() {
        setFilterChipActive(chipDraft);
        if (statusFilter != null) {
            statusFilter.setValue(StatusDisplay.label("DRAFT"));
        }
        refreshTree();
    }

    @FXML
    public void filterChipInactive() {
        setFilterChipActive(chipInactive);
        if (statusFilter != null) {
            statusFilter.setValue(StatusDisplay.label("INACTIVE"));
        }
        refreshTree();
    }

    private void setFilterChipActive(Button activeChip) {
        for (Button chip : List.of(chipAll, chipActive, chipDraft, chipInactive)) {
            if (chip != null) {
                chip.getStyleClass().remove("active");
            }
        }
        if (activeChip != null && !activeChip.getStyleClass().contains("active")) {
            activeChip.getStyleClass().add("active");
        }
    }

    private void syncFilterChipsWithCombo(String val) {
        if (val == null || StatusDisplay.isAll(val)) {
            setFilterChipActive(chipAll);
        } else if (val.contains("Đang hoạt động") || val.contains("Active")) {
            setFilterChipActive(chipActive);
        } else if (val.contains("Bản nháp") || val.contains("Draft")) {
            setFilterChipActive(chipDraft);
        } else if (val.contains("Không hoạt động") || val.contains("Inactive")) {
            setFilterChipActive(chipInactive);
        }
    }

    private void setupSearchFilter() {
        if (searchField == null) return;
        searchField.textProperty().addListener((obs, o, n) -> refreshTree());
    }

    // ── Drag & drop ──

    private void setupDragDrop() {
        projectTree.setOnDragDetected(e -> {
            TreeItem<Object> dragItem = projectTree.getSelectionModel().getSelectedItem();
            if (dragItem != null) {
                NodeRef dragRef = getNodeRef(dragItem);
                if (dragRef != null && ("suite".equals(dragRef.type) || "case".equals(dragRef.type))) {
                    Dragboard db = projectTree.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(dragItem.getValue() != null ? dragItem.getValue().toString() : "");
                    db.setContent(content);
                    e.consume();
                }
            }
        });

        projectTree.setOnDragOver(e -> {
            TreeItem<Object> targetItem = getTargetItemForDrag(projectTree, e.getX(), e.getY());
            if (targetItem != null && !targetItem.equals(projectTree.getSelectionModel().getSelectedItem())) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        projectTree.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                TreeItem<Object> sourceItem = projectTree.getSelectionModel().getSelectedItem();
                TreeItem<Object> targetItem = getTargetItemForDrag(projectTree, e.getX(), e.getY());
                if (sourceItem != null && targetItem != null) {
                    performSwapDragDrop(sourceItem, targetItem);
                }
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    private TreeItem<Object> getTargetItemForDrag(TreeView<Object> tree, double x, double y) {
        if (tree.getRoot() == null) return null;
        for (javafx.scene.Node node : tree.lookupAll(".tree-cell")) {
            if (node instanceof TreeCell<?> cell && !cell.isEmpty()
                    && cell.getBoundsInParent().contains(x, y)) {
                @SuppressWarnings("unchecked")
                TreeItem<Object> item = (TreeItem<Object>) cell.getTreeItem();
                return item;
            }
        }
        return null;
    }

    private void performSwapDragDrop(TreeItem<Object> source, TreeItem<Object> target) {
        NodeRef sourceRef = getNodeRef(source);
        NodeRef targetRef = getNodeRef(target);
        if (sourceRef == null || targetRef == null) return;

        // Cross-group suite move: drop suite onto a different group
        if ("suite".equals(sourceRef.type) && "group".equals(targetRef.type)
                && sourceRef.suitePath != null) {
            try {
                Path fromDir = Path.of(sourceRef.suitePath);
                Path toGroupDir = resolveAbsoluteGroupDir(target);
                if (toGroupDir == null) {
                    ToastService.warn("⚠️ Không thể xác định thư mục nhóm đích");
                    return;
                }
                if (fromDir.getParent() != null && fromDir.getParent().equals(toGroupDir)) {
                    ToastService.info("ℹ️ Suite đã có trong nhóm này");
                    return;
                }
                projectStore.moveSuite(fromDir, toGroupDir);
                refreshTree();
                ToastService.success("📁 Đã di chuyển suite '" + sourceRef.name + "' sang nhóm '" + targetRef.name + "'");
            } catch (IOException e) {
                ToastService.error("❌ Di chuyển thất bại: " + e.getMessage());
            }
            return;
        }

        // Cases cannot leave their suite via DND
        if ("case".equals(sourceRef.type) && !"case".equals(targetRef.type)
                && !"suite".equals(targetRef.type)) {
            ToastService.warn("⚠️ Case chỉ có thể sắp xếp lại trong suite của nó");
            return;
        }

        // Only allow reorder within same parent
        String sourceParent = sourceRef.parentName != null ? sourceRef.parentName
                : (source.getParent() != null && getNodeRef(source.getParent()) != null
                        ? getNodeRef(source.getParent()).name : null);
        String targetParent = targetRef.parentName != null ? targetRef.parentName
                : (target.getParent() != null && getNodeRef(target.getParent()) != null
                        ? getNodeRef(target.getParent()).name : null);

        if (!Objects.equals(sourceParent, targetParent)) {
            if ("suite".equals(sourceRef.type) && "suite".equals(targetRef.type)
                    && target.getParent() != null) {
                // Dropping onto a suite in another group → move into that suite's parent group
                NodeRef targetGroupRef = getNodeRef(target.getParent());
                if (targetGroupRef != null && "group".equals(targetGroupRef.type)
                        && sourceRef.suitePath != null) {
                    try {
                        Path fromDir = Path.of(sourceRef.suitePath);
                        Path toGroupDir = resolveAbsoluteGroupDir(target.getParent());
                        if (toGroupDir != null && (fromDir.getParent() == null
                                || !fromDir.getParent().equals(toGroupDir))) {
                            projectStore.moveSuite(fromDir, toGroupDir);
                            refreshTree();
                            ToastService.success("📁 Đã di chuyển suite '" + sourceRef.name
                                    + "' sang nhóm '" + targetGroupRef.name + "'");
                            return;
                        }
                    } catch (IOException e) {
                        ToastService.error("❌ Di chuyển thất bại: " + e.getMessage());
                        return;
                    }
                }
            }
            ToastService.warn("⚠️ Chỉ có thể sắp xếp trong cùng cấp cha");
            return;
        }

        if (source == target) return;

        List<TreeItem<Object>> children = source.getParent().getChildren();
        int sourceIdx = children.indexOf(source);
        int targetIdx = children.indexOf(target);
        if (sourceIdx < 0 || targetIdx < 0) return;

        children.remove(sourceIdx);
        int insertIdx = sourceIdx < targetIdx ? targetIdx - 1 : targetIdx;
        children.add(insertIdx, source);

        if ("case".equals(sourceRef.type) && sourceRef.suitePath != null && !sourceRef.suitePath.isBlank()) {
            try {
                List<String> orderedIds = new ArrayList<>();
                for (TreeItem<Object> child : children) {
                    NodeRef cr = getNodeRef(child);
                    if (cr != null && "case".equals(cr.type) && cr.caseId != null) {
                        orderedIds.add(cr.caseId);
                    }
                }
                projectStore.reorderCases(Path.of(sourceRef.suitePath), orderedIds);
            } catch (IOException e) {
                ToastService.error("❌ Không lưu được thứ tự case: " + e.getMessage());
                refreshTree();
                return;
            }
        }
        ToastService.success("📋 Đã sắp xếp lại: " + sourceRef.name);
    }

    private Path resolveAbsoluteGroupDir(TreeItem<Object> groupItem) {
        if (groupItem == null) return null;
        NodeRef ref = getNodeRef(groupItem);
        if (ref == null || !"group".equals(ref.type)) return null;
        if (groupItem.getParent() == null || groupItem == projectTree.getRoot()) {
            return rootProjectDir;
        }
        List<String> segments = new ArrayList<>();
        TreeItem<Object> cur = groupItem;
        while (cur != null && cur != projectTree.getRoot()) {
            NodeRef r = getNodeRef(cur);
            if (r != null && "group".equals(r.type)) {
                segments.add(0, r.name);
            }
            cur = cur.getParent();
        }
        Path path = rootProjectDir;
        for (String seg : segments) {
            path = path.resolve(seg);
        }
        return path;
    }

    // ── Refresh tree ──

    /** Reload tree from disk (also used when navigating back to Explorer). */
    public void refreshTree() {
        reloadTree();
    }

    /** Silent reload used when navigating back to Explorer. */
    public void reloadFromDisk() {
        reloadTree();
    }

    private void reloadTree() {
        String filter = statusFilter != null ? statusFilter.getValue() : StatusDisplay.ALL_LABEL;
        String search = searchField != null ? searchField.getText() : "";
        loadTree();
        boolean filterStatus = filter != null && !StatusDisplay.isAll(filter);
        boolean filterName = search != null && !search.isBlank();
        if ((filterStatus || filterName) && projectTree.getRoot() != null) {
            pruneTreeItem(projectTree.getRoot(), filter, search);
            expandMatchingBranches(projectTree.getRoot());
        }
        if (projectTree.getRoot() != null) {
            projectTree.getRoot().setExpanded(true);
        }
    }

    /**
     * Keep a node if it matches filters, or any descendant matches (so parents stay
     * to preserve the full folder path: Group → Suite → Case).
     * @return true if this node should remain in the tree
     */
    private boolean pruneTreeItem(TreeItem<Object> item, String statusFilter, String search) {
        if (item == null) return false;

        List<TreeItem<Object>> children = new ArrayList<>(item.getChildren());
        for (TreeItem<Object> child : children) {
            if (!pruneTreeItem(child, statusFilter, search)) {
                item.getChildren().remove(child);
            }
        }

        // Synthetic / hidden root always kept
        if (item.getParent() == null) {
            return true;
        }

        NodeRef ref = getNodeRef(item);
        boolean selfMatch = ref != null
                && nodeMatchesFilter(ref, statusFilter)
                && nameMatches(ref.name, search);
        boolean hasMatchingDescendant = !item.getChildren().isEmpty();
        return selfMatch || hasMatchingDescendant;
    }

    private void expandMatchingBranches(TreeItem<Object> item) {
        if (item == null) return;
        if (!item.getChildren().isEmpty()) {
            item.setExpanded(true);
            for (TreeItem<Object> child : item.getChildren()) {
                expandMatchingBranches(child);
            }
        }
    }

    private boolean nodeMatchesFilter(NodeRef ref, String filter) {
        if (ref == null) return true;
        if (filter == null || StatusDisplay.isAll(filter)) return true;
        // Groups have no lifecycle status in the filter sense — keep if descendants match
        if ("group".equals(ref.type)) {
            return true;
        }
        String key = StatusDisplay.normalize(ref.status);
        if ("Đang hoạt động".equals(filter) || filter.contains("Đang hoạt động") || filter.contains("Hoạt động")
                || "Active".equals(filter) || filter.startsWith("🟢") || filter.startsWith("●")) {
            return "ACTIVE".equals(key) || "READY".equals(key);
        }
        if ("Bản nháp".equals(filter) || filter.contains("Bản nháp")
                || "Draft".equals(filter) || filter.startsWith("📝") || filter.startsWith("🟨")) {
            return "DRAFT".equals(key);
        }
        if ("Không hoạt động".equals(filter) || filter.contains("Không hoạt động")
                || "Inactive".equals(filter)
                || filter.startsWith("⏸️") || filter.startsWith("⏸")
                || filter.startsWith("⬛")) {
            return "INACTIVE".equals(key);
        }
        return true;
    }

    private static boolean nameMatches(String name, String query) {
        if (query == null || query.isBlank()) return true;
        if (name == null) return false;
        return name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    // ── CRUD operations ──

    @FXML
    public void addGroup() {
        Path parentDir = resolveSelectedGroupDir();
        String parentLabel = parentDir.equals(rootProjectDir)
                ? null
                : parentDir.getFileName().toString();

        EntityFormDialog.showCreateGroup(parentLabel).ifPresent(form -> {
            try {
                projectStore.createGroup(parentDir, form.name(), form.description());
                refreshTree();
                ToastService.success("✅ Đã tạo nhóm: " + form.name());
            } catch (IOException e) {
                ToastService.error("❌ Không thể tạo nhóm: " + e.getMessage());
            }
        });
    }

    @FXML
    public void addSuite() {
        TreeItem<Object> selected = resolveTarget();
        NodeRef ref = selected != null ? getNodeRef(selected) : null;

        Path groupDir;
        String targetLabel;
        if (selected == null || isRootItem(selected)) {
            groupDir = rootProjectDir;
            targetLabel = null;
        } else if (ref != null && "group".equals(ref.type)) {
            groupDir = resolveGroupDir(ref);
            targetLabel = ref.name;
        } else {
            ToastService.warn("⚠️ Chọn một Nhóm trước khi tạo Suite");
            return;
        }

        EntityFormDialog.showCreateSuite(targetLabel).ifPresent(form -> {
            try {
                projectStore.createSuite(
                        groupDir,
                        form.name(),
                        form.description(),
                        form.status(),
                        form.browser(),
                        form.headless());
                refreshTree();
                ToastService.success("✅ Đã tạo suite: " + form.name());
            } catch (IOException e) {
                ToastService.error("❌ Không thể tạo suite: " + e.getMessage());
            }
        });
    }

    @FXML
    public void addCase() {
        TreeItem<Object> selected = resolveTarget();
        NodeRef ref = selected != null ? getNodeRef(selected) : null;
        if (ref == null || (!"suite".equals(ref.type) && !"case".equals(ref.type))) {
            ToastService.warn("⚠️ Chọn một Suite (hoặc Case trong suite) để tạo Case mới");
            return;
        }

        Path suiteDir = resolveSuitePath(ref);
        if (suiteDir == null || !Files.isDirectory(suiteDir)) {
            ToastService.error("❌ Không tìm thấy thư mục suite");
            return;
        }
        String suiteName = "suite".equals(ref.type) ? ref.name : ref.parentName;

        EntityFormDialog.showCreateCase(suiteName).ifPresent(form -> {
            try {
                projectStore.createCase(
                        suiteDir,
                        form.name(),
                        form.description(),
                        form.status(),
                        form.startUrl(),
                        form.browser(),
                        form.headless());
                refreshTree();
                ToastService.success("✅ Đã tạo case: " + form.name());
                if (form.startRecord()) {
                    openWorkspaceForSuiteCase(suiteDir, suiteName, form.name(), true);
                }
            } catch (IOException e) {
                ToastService.error("❌ Không thể tạo case: " + e.getMessage());
            }
        });
    }

    private void changeSuiteStatus(String newStatus) {
        TreeItem<Object> selected = resolveTarget();
        NodeRef ref = selected != null ? getNodeRef(selected) : null;
        if (ref == null || !"suite".equals(ref.type)) {
            ToastService.warn("⚠️ Chuột phải / chọn một Suite để đổi trạng thái");
            return;
        }
        String target = StatusDisplay.normalize(newStatus);
        String current = StatusDisplay.normalize(ref.status);
        if (target.equals(current)) {
            return;
        }
        TreeItem<Object> keepSel = projectTree.getSelectionModel().getSelectedItem();
        NodeRef keepRef = keepSel != null ? getNodeRef(keepSel) : null;
        String keepType = keepRef != null ? keepRef.type : null;
        String keepName = keepRef != null ? keepRef.name : null;

        Path suiteDir = resolveSuitePath(ref);
        try {
            projectStore.updateSuiteStatus(suiteDir, target);
            String suiteName = ref.name;
            contextTarget = null;
            refreshTree();
            if (keepType != null && keepName != null) {
                reselectByTypeAndName(keepType, keepName);
            }
            ToastService.success("✅ Suite \"" + suiteName + "\": "
                    + StatusDisplay.label(current) + " → " + StatusDisplay.label(target));
        } catch (IOException e) {
            ToastService.error("❌ Không đổi được trạng thái suite: " + e.getMessage());
        }
    }

    private void reselectByTypeAndName(String type, String name) {
        if (type == null || name == null || projectTree.getRoot() == null) return;
        TreeItem<Object> found = findTreeItemByTypeAndName(projectTree.getRoot(), type, name);
        if (found != null) {
            projectTree.getSelectionModel().select(found);
            projectTree.scrollTo(projectTree.getRow(found));
            updatePreview(found);
        } else {
            projectTree.getSelectionModel().clearSelection();
            showRootPreview();
        }
    }

    private TreeItem<Object> findTreeItemByTypeAndName(TreeItem<Object> node, String type, String name) {
        if (node == null) return null;
        NodeRef ref = getNodeRef(node);
        if (ref != null && type.equals(ref.type) && name.equals(ref.name)) {
            return node;
        }
        for (TreeItem<Object> child : node.getChildren()) {
            TreeItem<Object> hit = findTreeItemByTypeAndName(child, type, name);
            if (hit != null) return hit;
        }
        return null;
    }

    @FXML
    public void editFromPreview() {
        editSelectedEntity();
    }

    @FXML
    public void editSelectedEntity() {
        TreeItem<Object> selected = resolveTarget();
        if (selected == null) {
            ToastService.warn("⚠️ Chưa chọn mục nào");
            return;
        }
        if (isRootItem(selected)) {
            ToastService.warn("⚠️ Không thể sửa gốc dự án");
            return;
        }
        NodeRef ref = getNodeRef(selected);
        if (ref == null) {
            ToastService.warn("⚠️ Không đọc được mục đã chọn");
            return;
        }
        try {
            if ("group".equals(ref.type)) {
                editGroup(ref);
            } else if ("suite".equals(ref.type)) {
                editSuite(ref);
            } else if ("case".equals(ref.type)) {
                editCase(ref);
            }
        } catch (IOException e) {
            ToastService.error("❌ Không thể sửa: " + e.getMessage());
        }
    }

    private void editGroup(NodeRef ref) throws IOException {
        Path groupDir = resolveGroupDir(ref);
        TestGroup group = projectStore.loadGroup(groupDir);
        EntityFormDialog.Result seed = new EntityFormDialog.Result(
                group.getName(),
                group.getDescription() != null ? group.getDescription() : "",
                null, null, null, null);
        EntityFormDialog.showEditGroup(seed).ifPresent(form -> {
            try {
                Path dir = groupDir;
                if (!form.name().equals(ref.name)) {
                    projectStore.renameGroup(dir, form.name());
                    dir = dir.resolveSibling(form.name());
                }
                projectStore.updateGroupMeta(dir, form.description());
                refreshTree();
                reselectByTypeAndName("group", form.name());
                ToastService.success("✅ Đã cập nhật nhóm");
            } catch (IOException e) {
                ToastService.error("❌ Không lưu được nhóm: " + e.getMessage());
            }
        });
    }

    private void editSuite(NodeRef ref) throws IOException {
        Path suiteDir = resolveSuitePath(ref);
        TestSuite suite = projectStore.loadAndMigrateSuite(suiteDir);
        EntityFormDialog.Result seed = new EntityFormDialog.Result(
                suite.getName(),
                suite.getDescription() != null ? suite.getDescription() : "",
                StatusDisplay.normalize(suite.getStatus()),
                null,
                suite.getBrowser(),
                suite.getHeadless());
        EntityFormDialog.showEditSuite(seed).ifPresent(form -> {
            try {
                Path dir = suiteDir;
                projectStore.updateSuiteMeta(
                        dir,
                        form.description(),
                        form.status(),
                        form.browser(),
                        form.headless());
                if (!form.name().equals(ref.name)) {
                    projectStore.renameSuite(dir, form.name());
                    dir = dir.resolveSibling(form.name());
                }
                refreshTree();
                reselectByTypeAndName("suite", form.name());
                ToastService.success("✅ Đã cập nhật suite");
            } catch (IOException e) {
                ToastService.error("❌ Không lưu được suite: " + e.getMessage());
            }
        });
    }

    private void editCase(NodeRef ref) throws IOException {
        Path suiteDir = resolveSuitePath(ref);
        TestSuite suite = projectStore.loadAndMigrateSuite(suiteDir);
        TestCase tc = suite.getCases().stream()
                .filter(c -> ref.caseId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new IOException("Không tìm thấy case"));
        String caseStatus = normalizeCaseStatusForForm(tc.getStatus());
        EntityFormDialog.Result seed = new EntityFormDialog.Result(
                tc.getName(),
                tc.getDescription() != null ? tc.getDescription() : "",
                caseStatus,
                tc.getStartUrl() != null ? tc.getStartUrl() : "",
                tc.getBrowser(),
                tc.getHeadless());
        EntityFormDialog.showEditCase(seed).ifPresent(form -> {
            try {
                projectStore.updateCaseMeta(
                        suiteDir,
                        ref.caseId,
                        form.description(),
                        form.status(),
                        form.startUrl(),
                        form.browser(),
                        form.headless());
                String finalName = form.name();
                if (!form.name().equals(ref.name)) {
                    projectStore.renameCase(suiteDir, ref.caseId, form.name());
                }
                refreshTree();
                reselectByTypeAndName("case", finalName);
                ToastService.success("✅ Đã cập nhật case");
                if (form.startRecord()) {
                    String suiteName = suite.getName() != null ? suite.getName() : suiteDir.getFileName().toString();
                    openWorkspaceForSuiteCase(suiteDir, suiteName, finalName, true);
                }
            } catch (IOException e) {
                ToastService.error("❌ Không lưu được case: " + e.getMessage());
            }
        });
    }

    private static String normalizeCaseStatusForForm(String status) {
        if (status == null || status.isBlank()) return "Draft";
        if ("INACTIVE".equalsIgnoreCase(status) || "Inactive".equalsIgnoreCase(status)) {
            return "Inactive";
        }
        if ("READY".equalsIgnoreCase(status) || "Ready".equalsIgnoreCase(status)
                || "ACTIVE".equalsIgnoreCase(status)) {
            return "Ready";
        }
        return "Draft";
    }

    @FXML
    public void rename() {
        TreeItem<Object> selected = resolveTarget();
        if (selected == null) {
            ToastService.warn("⚠️ Chưa chọn mục nào");
            return;
        }
        if (isRootItem(selected)) {
            ToastService.warn("⚠️ Không thể đổi tên gốc dự án");
            return;
        }

        NodeRef ref = getNodeRef(selected);
        if (ref == null) {
            ToastService.warn("⚠️ Không đọc được mục đã chọn");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(ref.name);
        DialogTheme.apply(dialog);
        dialog.setTitle("Đổi tên");
        dialog.setHeaderText("Đổi tên: " + capitalize(ref.type));
        dialog.setContentText("Tên mới:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.trim().isEmpty()) {
                ToastService.warn("⚠️ Tên không được để trống");
                return;
            }
            try {
                if ("group".equals(ref.type)) {
                    projectStore.renameGroup(resolveGroupDir(ref), newName.trim());
                } else if ("suite".equals(ref.type)) {
                    Path suiteDir = resolveSuitePath(ref);
                    projectStore.renameSuite(suiteDir, newName.trim());
                } else if ("case".equals(ref.type)) {
                    Path suiteDir = resolveSuitePath(ref);
                    projectStore.renameCase(suiteDir, ref.caseId, newName.trim());
                }
                refreshTree();
                ToastService.success("✅ Đã đổi tên thành: " + newName);
            } catch (IOException e) {
                ToastService.error("❌ Không thể đổi tên: " + e.getMessage());
            }
        });
    }

    @FXML
    public void deleteItem() {
        TreeItem<Object> selected = resolveTarget();
        if (selected == null) {
            ToastService.warn("⚠️ Chưa chọn mục nào");
            return;
        }
        if (isRootItem(selected)) {
            ToastService.warn("⚠️ Không thể xóa gốc dự án");
            return;
        }

        NodeRef ref = getNodeRef(selected);
        if (ref == null) {
            ToastService.warn("⚠️ Không đọc được mục đã chọn");
            return;
        }
        int childCount = 0;
        String scope = capitalize(ref.type);

        if ("group".equals(ref.type)) {
            childCount = countGroupsUnderItem(selected) + countSuitesUnderItem(selected)
                    + countCasesUnderItem(selected);
        } else if ("suite".equals(ref.type)) {
            childCount = countCasesInItem(selected);
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Xóa " + scope + " '" + ref.name + "'?");
        if ("group".equals(ref.type)) {
            confirm.setContentText(childCount > 0
                    ? "Sẽ xóa toàn bộ dữ liệu con của nhóm này (" + childCount
                    + " mục: nhóm con, suite, case và file liên quan). Không thể hoàn tác."
                    : "Hành động này không thể hoàn tác.");
        } else if ("suite".equals(ref.type)) {
            confirm.setContentText(childCount > 0
                    ? "Sẽ xóa toàn bộ case và file script trong suite (" + childCount
                    + " case). Không thể hoàn tác."
                    : "Hành động này không thể hoàn tác.");
        } else {
            confirm.setContentText("Case và file script liên quan sẽ bị xóa. Không thể hoàn tác.");
        }

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    if ("group".equals(ref.type)) {
                        projectStore.deleteGroup(resolveGroupDir(ref));
                    } else if ("suite".equals(ref.type)) {
                        Path suiteDir = resolveSuitePath(ref);
                        projectStore.deleteSuite(suiteDir);
                    } else if ("case".equals(ref.type)) {
                        Path suiteDir = resolveSuitePath(ref);
                        projectStore.deleteCase(suiteDir, ref.caseId);
                    }
                    refreshTree();
                    projectTree.getSelectionModel().clearSelection();
                    showRootPreview();
                    ToastService.success("🗑️ Đã xóa: " + ref.name);
                } catch (IOException e) {
                    ToastService.error("❌ Không thể xóa: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    public void copyItem() {
        TreeItem<Object> selected = resolveTarget();
        if (selected == null) {
            ToastService.warn("⚠️ Chưa chọn mục nào");
            return;
        }

        NodeRef ref = getNodeRef(selected);
        if (ref == null) return;

        // Only suites and cases can be copied
        if ("group".equals(ref.type)) {
            ToastService.warn("⚠️ Không thể sao chép nhóm");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(ref.name + " (copy)");
        DialogTheme.apply(dialog);
        dialog.setTitle("Sao chép");
        dialog.setHeaderText("Sao chép " + ref.name);
        dialog.setContentText("Tên mới:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.trim().isEmpty()) {
                ToastService.warn("⚠️ Tên không được để trống");
                return;
            }
            try {
                if ("suite".equals(ref.type)) {
                    Path fromDir = resolveSuitePath(ref);
                    Path parentGroup = fromDir.getParent() != null ? fromDir.getParent() : rootProjectDir;
                    projectStore.copySuite(fromDir, parentGroup, newName.trim());
                } else if ("case".equals(ref.type)) {
                    Path suiteDir = resolveSuitePath(ref);
                    projectStore.copyCase(suiteDir, ref.caseId, newName.trim());
                }
                refreshTree();
                ToastService.success("📄 Đã sao chép thành: " + newName);
            } catch (IOException | RuntimeException e) {
                ToastService.error("❌ Không thể sao chép: " + e.getMessage());
            }
        });
    }

    // ── Run operations ──

    @FXML
    public void runSelected() {
        TreeItem<Object> selected = resolveTarget();
        if (selected == null) {
            selected = projectTree.getRoot();
        }
        if (selected == null) {
            ToastService.warn("⚠️ Chưa chọn mục để chạy");
            return;
        }

        NodeRef ref = getNodeRef(selected);
        if (ref == null) {
            ToastService.warn("⚠️ Không đọc được mục đã chọn");
            return;
        }

        SettingsStore.Settings settings;
        try {
            settings = new SettingsStore().load();
        } catch (IOException e) {
            ToastService.error("❌ Không thể tải cài đặt: " + e.getMessage());
            return;
        }

        final String scope;
        final Path scopePath;
        if (isRootItem(selected)) {
            scope = "ALL";
            scopePath = rootProjectDir;
        } else if ("group".equals(ref.type)) {
            scope = "GROUP";
            scopePath = resolveGroupDir(ref);
        } else if ("suite".equals(ref.type)) {
            if (!StatusDisplay.isActive(ref.status)) {
                ToastService.warn("⚠️ Suite chưa Đang hoạt động — mở Workspace → Thông tin Suite để kích hoạt");
                return;
            }
            scope = "SUITE";
            scopePath = resolveSuitePath(ref);
        } else {
            TreeItem<Object> parent = selected.getParent();
            NodeRef suiteRef = parent != null ? getNodeRef(parent) : null;
            String suiteSt = suiteRef != null ? suiteRef.status : null;
            if (!StatusDisplay.isActive(suiteSt)) {
                ToastService.warn("⚠️ Suite chưa Đang hoạt động — không chạy case được");
                return;
            }
            if (!StatusDisplay.isRunnable(ref.status)) {
                ToastService.warn("⚠️ Case chưa Sẵn sàng — ghi hình trước khi chạy");
                return;
            }
            scope = "CASE";
            Path suiteDir = resolveSuitePath(ref);
            String script = (ref.caseId != null)
                    ? findCaseScriptFile(suiteDir, ref)
                    : ref.name + ".java";
            scopePath = suiteDir.resolve(script);
        }

        final String browserId;
        final boolean headless;
        try {
            TestSuite suiteForRun = null;
            TestCase caseForRun = null;
            if ("suite".equals(ref.type) || "case".equals(ref.type)) {
                Path suiteDir = resolveSuitePath(ref);
                if (suiteDir != null) {
                    suiteForRun = projectStore.loadAndMigrateSuite(suiteDir);
                    if ("case".equals(ref.type) && ref.caseId != null) {
                        final String cid = ref.caseId;
                        caseForRun = suiteForRun.getCases().stream()
                                .filter(c -> cid.equals(c.getId()))
                                .findFirst()
                                .orElse(null);
                    }
                }
            }
            browserId = RunTargetResolve.resolveBrowser(caseForRun, suiteForRun);
            headless = RunTargetResolve.resolveHeadless(caseForRun, suiteForRun, settings.isHeadlessDefault());
        } catch (IOException e) {
            ToastService.error("❌ Không đọc được cấu hình chạy: " + e.getMessage());
            return;
        }
        final boolean stopOnFail = settings.isStopOnFirstFailureDefault();
        final boolean shareCtx = settings.isShareContextDefault();
        final String targetName = ref.name;

        StatusBarService.set("Đang chạy: " + scope + " " + targetName);

        BusyButtons.runAsync(
                runNodeBtn,
                "Đang chạy…",
                () -> {
                    TestRunnerService runner = new TestRunnerService(projectStore, reportStore);
                    return runner.run(scope, scopePath, headless, browserId, stopOnFail, shareCtx, msg -> {});
                },
                report -> {
                    StatusBarService.ready();
                    reloadFromDisk();
                    if (report.getTotal() == 0) {
                        ToastService.warn("⚠️ Không có test case nào để chạy — không lưu báo cáo");
                        return;
                    }
                    ToastService.success(String.format(
                            "✅ Hoàn tất: %d đạt, %d thất bại, %d bỏ qua",
                            report.getPassed(), report.getFailed(), report.getSkipped()));
                    ViewNavigator.navigateToReportDetail(report.getRunId());
                },
                ex -> {
                    StatusBarService.ready();
                    ToastService.error("❌ Chạy thất bại: " + ex.getMessage());
                }
        );
    }

    @FXML
    public void runNode() {
        runSelected();
    }

    /** Entry point for shell "Run All" — runs on caller thread (BusyButtons wraps it). */
    public RunReport runAllBlocking() throws IOException {
        SettingsStore.Settings settings = new SettingsStore().load();
        String browserId = settings.getBrowser();
        StatusBarService.set("Đang chạy tất cả (" + browserId + ")");
        TestRunnerService runner = new TestRunnerService(projectStore, reportStore);
        RunReport report = runner.run("ALL", rootProjectDir,
                settings.isHeadlessDefault(), browserId,
                settings.isStopOnFirstFailureDefault(),
                settings.isShareContextDefault(),
                msg -> {});
        if (report.getTotal() == 0) {
            ToastService.warn("⚠️ Không có test case nào để chạy — không lưu báo cáo");
        } else {
            String resultMsg = String.format("🏁 Chạy tất cả hoàn tất: %d đạt, %d thất bại, %d bỏ qua",
                    report.getPassed(), report.getFailed(), report.getSkipped());
            ToastService.success(resultMsg);
        }
        return report;
    }

    /** Convenience for direct calls that should show button busy state. */
    public void runAll() {
        StatusBarService.set("Đang chạy tất cả…");
        BusyButtons.runAsync(
                runNodeBtn,
                "Đang chạy…",
                this::runAllBlocking,
                report -> {
                    StatusBarService.ready();
                    reloadFromDisk();
                    if (report != null && report.getTotal() > 0) {
                        ViewNavigator.navigateToReportDetail(report.getRunId());
                    }
                },
                ex -> {
                    StatusBarService.ready();
                    ToastService.error("❌ Chạy tất cả thất bại: " + ex.getMessage());
                }
        );
    }

    // ── Workspace navigation ──

    @FXML
    public void openWorkspace() {
        TreeItem<Object> selected = resolveTarget();
        if (selected == null) {
            ToastService.warn("⚠️ Chưa chọn mục nào");
            return;
        }

        NodeRef ref = getNodeRef(selected);
        if (ref == null || (!"suite".equals(ref.type) && !"case".equals(ref.type))) {
            ToastService.warn("⚠️ Chỉ có thể mở suite / case trong workspace");
            return;
        }
        openWorkspaceForRef(ref);
    }

    private void openWorkspaceForRef(NodeRef ref) {
        Path suiteDir = resolveSuitePath(ref);
        if (suiteDir == null || !Files.isDirectory(suiteDir)) {
            ToastService.error("⚠️ Không tìm thấy thư mục suite");
            return;
        }
        TestSuite suite;
        try {
            suite = projectStore.loadAndMigrateSuite(suiteDir);
        } catch (IOException e) {
            ToastService.error("❌ Không đọc được suite: " + e.getMessage());
            return;
        }
        String suiteName = suite.getName() != null ? suite.getName() : suiteDir.getFileName().toString();
        String caseToSelect = "case".equals(ref.type) ? ref.name : null;

        SuiteWorkspacePageController ctrl = ViewNavigator.navigateToWorkspace(
                ref.parentName, suiteName, suiteDir);
        if (ctrl != null) {
            ctrl.loadSuite(suite, suiteDir, caseToSelect);
        }
    }

    private void openWorkspaceForSuiteCase(Path suiteDir, String suiteName, String caseName, boolean startRecord) {
        if (suiteDir == null || !Files.isDirectory(suiteDir)) {
            ToastService.error("⚠️ Không tìm thấy thư mục suite");
            return;
        }
        TestSuite suite;
        try {
            suite = projectStore.loadAndMigrateSuite(suiteDir);
        } catch (IOException e) {
            ToastService.error("❌ Không đọc được suite: " + e.getMessage());
            return;
        }
        String resolvedSuiteName = suiteName != null && !suiteName.isBlank()
                ? suiteName
                : (suite.getName() != null ? suite.getName() : suiteDir.getFileName().toString());
        Path parent = suiteDir.getParent();
        String groupLabel = (parent != null && !parent.equals(rootProjectDir))
                ? parent.getFileName().toString()
                : null;
        SuiteWorkspacePageController ctrl = ViewNavigator.navigateToWorkspace(groupLabel, resolvedSuiteName, suiteDir);
        if (ctrl != null) {
            ctrl.loadSuite(suite, suiteDir, caseName, startRecord);
        }
    }

    // ── Utility ──

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String findCaseScriptFile(Path suiteDir, NodeRef ref) {
        try {
            TestSuite suite = projectStore.loadAndMigrateSuite(suiteDir);
            return suite.getCases().stream()
                    .filter(c -> Objects.equals(c.getId(), ref.caseId)
                            || Objects.equals(c.getName(), ref.name))
                    .map(c -> c.getScriptFile() != null && !c.getScriptFile().isBlank()
                            ? c.getScriptFile()
                            : c.getName() + ".java")
                    .findFirst()
                    .orElse(ref.name + ".java");
        } catch (IOException e) {
            return ref.name + ".java";
        }
    }

    private Path resolveSuitePath(NodeRef ref) {
        if (ref.suitePath != null && !ref.suitePath.isEmpty()) {
            return Path.of(ref.suitePath);
        }
        if (ref.dirPath != null && !ref.dirPath.isEmpty()) {
            return Path.of(ref.dirPath);
        }
        return rootProjectDir.resolve(ref.parentName != null ? ref.parentName : "").resolve(ref.name);
    }

    private Path resolveGroupDir(NodeRef ref) {
        if (ref != null && ref.dirPath != null && !ref.dirPath.isEmpty()) {
            return Path.of(ref.dirPath);
        }
        if (ref != null && "group".equals(ref.type)) {
            return rootProjectDir.resolve(ref.name);
        }
        return rootProjectDir;
    }

    /** Group dir for creating a nested group: selected group, or project root. */
    private Path resolveSelectedGroupDir() {
        TreeItem<Object> selected = resolveTarget();
        NodeRef ref = selected != null ? getNodeRef(selected) : null;
        if (ref == null) {
            return rootProjectDir;
        }
        if ("group".equals(ref.type)) {
            return resolveGroupDir(ref);
        }
        if ("suite".equals(ref.type) || "case".equals(ref.type)) {
            Path suiteDir = resolveSuitePath(ref);
            return suiteDir.getParent() != null ? suiteDir.getParent() : rootProjectDir;
        }
        return rootProjectDir;
    }
}
