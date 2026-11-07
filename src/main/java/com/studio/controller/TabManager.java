package com.studio.controller;

import com.studio.util.UiMotion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages the Multi-Tab Document Bar (VS Code / IntelliJ style) in the main
 * shell.
 * Allows multiple Suites, Explorer, Reports, and AI Assistant to be open
 * concurrently.
 */
public class TabManager {

    public static class StudioTab {
        private final String id;
        private String title;
        private final String icon;
        private final Node content;
        private final boolean closable;
        private boolean dirty;
        private final Runnable onClose;
        private final Object controller;

        // UI nodes
        private HBox tabNode;
        private Label titleLabel;
        private Label dirtyLabel;
        private Button closeButton;

        public StudioTab(String id, String title, String icon, Node content,
                boolean closable, Runnable onClose, Object controller) {
            this.id = id;
            this.title = title;
            this.icon = icon != null ? icon : "";
            this.content = content;
            this.closable = closable;
            this.onClose = onClose;
            this.controller = controller;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Node getContent() {
            return content;
        }

        public boolean isClosable() {
            return closable;
        }

        public boolean isDirty() {
            return dirty;
        }

        public Object getController() {
            return controller;
        }
    }

    private final HBox tabContainer;
    private final StackPane contentArea;
    private final Map<String, StudioTab> tabs = new LinkedHashMap<>();
    private String activeTabId;
    private Consumer<StudioTab> onTabSelectedListener;

    public TabManager(HBox tabContainer, StackPane contentArea) {
        this.tabContainer = tabContainer;
        this.contentArea = contentArea;
    }

    public void setOnTabSelectedListener(Consumer<StudioTab> listener) {
        this.onTabSelectedListener = listener;
    }

    public void openTab(String id, String title, String icon, Node content,
            boolean closable, Runnable onClose, Object controller) {
        if (tabs.containsKey(id)) {
            // Already open, select it
            StudioTab existing = tabs.get(id);
            if (title != null && !title.equals(existing.title)) {
                updateTitle(id, title);
            }
            selectTab(id);
            return;
        }

        StudioTab tab = new StudioTab(id, title, icon, content, closable, onClose, controller);
        HBox tabNode = buildTabNode(tab);
        tab.tabNode = tabNode;

        tabs.put(id, tab);
        tabContainer.getChildren().add(tabNode);

        selectTab(id);
    }

    private HBox buildTabNode(StudioTab tab) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("tab-item");
        row.setPadding(new Insets(5, 12, 5, 12));

        Label iconLbl = new Label(tab.icon != null ? tab.icon : "");
        iconLbl.getStyleClass().add("tab-icon");
        if (tab.icon == null || tab.icon.isBlank()) {
            iconLbl.setVisible(false);
            iconLbl.setManaged(false);
        }

        Label textLbl = new Label(tab.title);
        textLbl.getStyleClass().add("tab-title");
        tab.titleLabel = textLbl;

        Label dotLbl = new Label("●");
        dotLbl.getStyleClass().add("tab-dirty-dot");
        dotLbl.setVisible(false);
        dotLbl.setManaged(false);
        tab.dirtyLabel = dotLbl;

        row.getChildren().addAll(iconLbl, textLbl, dotLbl);

        if (tab.closable) {
            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("tab-close-btn");
            closeBtn.setTooltip(new Tooltip("Đóng tab"));
            closeBtn.setOnAction(e -> {
                closeTab(tab.id);
                e.consume();
            });
            tab.closeButton = closeBtn;
            row.getChildren().add(closeBtn);
        }

        row.setOnMouseClicked(e -> {
            selectTab(tab.id);
            e.consume();
        });

        return row;
    }

    public void selectTab(String id) {
        if (!tabs.containsKey(id))
            return;
        activeTabId = id;

        for (Map.Entry<String, StudioTab> entry : tabs.entrySet()) {
            StudioTab t = entry.getValue();
            boolean isActive = entry.getKey().equals(id);
            if (t.tabNode != null) {
                if (isActive) {
                    if (!t.tabNode.getStyleClass().contains("active")) {
                        t.tabNode.getStyleClass().add("active");
                    }
                } else {
                    t.tabNode.getStyleClass().remove("active");
                }
            }
        }

        StudioTab selected = tabs.get(id);
        if (selected != null && contentArea != null && selected.content != null) {
            contentArea.getChildren().setAll(selected.content);
            UiMotion.playPageEnter(selected.content);

            if (onTabSelectedListener != null) {
                onTabSelectedListener.accept(selected);
            }
        }
    }

    public void closeTab(String id) {
        if (!tabs.containsKey(id))
            return;

        StudioTab tab = tabs.get(id);
        if (tab.onClose != null) {
            tab.onClose.run();
        }

        int index = new ArrayList<>(tabs.keySet()).indexOf(id);
        tabs.remove(id);
        if (tab.tabNode != null) {
            tabContainer.getChildren().remove(tab.tabNode);
        }

        // If the closed tab was active, switch to adjacent or remaining
        if (id.equals(activeTabId)) {
            if (!tabs.isEmpty()) {
                List<String> keys = new ArrayList<>(tabs.keySet());
                int nextIndex = Math.min(index, keys.size() - 1);
                selectTab(keys.get(nextIndex));
            } else {
                activeTabId = null;
                // Default back to Explorer
                ViewNavigator.navigateTo(ViewNavigator.Page.EXPLORER);
            }
        }
    }

    public void setDirty(String id, boolean dirty) {
        StudioTab tab = tabs.get(id);
        if (tab != null && tab.dirtyLabel != null) {
            tab.dirty = dirty;
            tab.dirtyLabel.setVisible(dirty);
            tab.dirtyLabel.setManaged(dirty);
        }
    }

    public void updateTitle(String id, String newTitle) {
        StudioTab tab = tabs.get(id);
        if (tab != null && tab.titleLabel != null) {
            tab.title = newTitle;
            tab.titleLabel.setText(newTitle);
        }
    }

    public String getActiveTabId() {
        return activeTabId;
    }

    public StudioTab getTab(String id) {
        return tabs.get(id);
    }

    public boolean hasTab(String id) {
        return tabs.containsKey(id);
    }
}
