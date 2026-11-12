/* SPDX-License-Identifier: MIT */

package atlantafx.sampler.page.components;

import atlantafx.base.controls.*;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import atlantafx.base.util.NullSafety;
import atlantafx.sampler.page.AbstractPage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

public final class SidebarPage extends AbstractPage {

    public static final String NAME = "Sidebar";

    @Override
    public String getName() {
        return NAME;
    }

    public SidebarPage() {
        super();

        addPageHeader();
        addFormattedText("""
            A side navigation component that supports collapsing, item selection, \
            header, footer, and transition animations."""
        );
        addNode(new Playground());
    }

    public class Playground extends BorderPane {

        private final Sidebar<String> sidebar = new Sidebar<>();
        private final SidebarHeader<String> defaultHeader;
        private final SidebarFooter<String> defaultFooter;

        private final TextArea logArea = new TextArea();
        private ContentPane contentPane = NullSafety.lateNonNull();

        private boolean hoverExpandEnabled = false;
        private int counter = 1;

        public Playground() {
            defaultHeader = new SidebarHeader<>(
                FAKER.company().name(), new FontIcon(randomIcon())
            );
            sidebar.setHeader(defaultHeader);

            defaultFooter = new SidebarFooter<>(
                FAKER.name().fullName(), new FontIcon(randomIcon()), createBadge("Online")
            );
            sidebar.setFooter(defaultFooter);
            sidebar.setFooterMenu(createFooterMenu());

            sidebar.addEventHandler(SidebarEvent.ITEM_CLICK, e ->
                log("Item click: " + e.getItem().getText())
            );
            sidebar.getSelectionModel().selectedItemProperty().addListener((_, _, val) -> {
                //noinspection all
                if (val != null) {
                    log("Selected: " + val.getText());
                }
            });

            sidebar.setPrefWidth(300);
            sidebar.setPrefHeight(600);
            toggleHoverExpansion(sidebar);

            logArea.setEditable(false);
            logArea.setPrefRowCount(16);

            var controlPane = new ControlPane(this);
            BorderPane.setMargin(controlPane, new Insets(10));
            setTop(controlPane);

            contentPane = new ContentPane(sidebar, logArea);
            BorderPane.setMargin(contentPane, new Insets(10));
            setCenter(contentPane);

            populateInitialItems(10);
        }

        private void populateInitialItems(int count) {
            sidebar.getItems().clear();

            var items = generate(() -> {
                int type = RANDOM.nextInt(3);
                return switch (type) {
                    case 0 -> new SidebarNav<String>(
                        FAKER.commerce().department(), new FontIcon(randomIcon()), randomAccessory()
                    );
                    case 1 -> {
                        var group = new SidebarGroup<String>(
                            FAKER.commerce().department(), new FontIcon(randomIcon()), randomAccessory()
                        );
                        var children = generate(
                            () -> new SidebarNav<String>(
                                FAKER.commerce().productName(), new FontIcon(randomIcon()), randomAccessory()
                            ),
                            RANDOM.nextInt(1, 4)
                        );
                        group.getItems().addAll(children);
                        yield group;
                    }
                    default -> new SidebarSection<String>(FAKER.job().title(), randomAccessory());
                };
            }, count);

            sidebar.getItems().addAll(items);
        }

        public ContextMenu createFooterMenu() {
            var profile = new MenuItem("User Profile", new FontIcon(randomIcon()));
            profile.setOnAction(_ -> System.out.println("Click: User Profile"));

            var settings = new MenuItem("Account Settings", new FontIcon(randomIcon()));
            settings.setOnAction(_ -> System.out.println("Click: Account Settings"));

            var notifications = new MenuItem("Notifications", new FontIcon(randomIcon()));
            notifications.setOnAction(_ -> System.out.println("Click: Notifications"));

            var logout = new MenuItem("Log Out", new FontIcon(randomIcon()));
            logout.setOnAction(_ -> System.out.println("Click: Log Out"));

            return new ContextMenu(profile, settings, notifications, new SeparatorMenuItem(), logout);
        }

        private void toggleHoverExpansion(Sidebar<String> sidebar) {
            sidebar.setOnMouseEntered(_ -> {
                if (hoverExpandEnabled && sidebar.isCollapsed()) {
                    sidebar.setCollapsed(false);
                    log("Hover: Auto-expanded");
                }
            });
            sidebar.setOnMouseExited(_ -> {
                if (hoverExpandEnabled && !sidebar.isCollapsed()) {
                    sidebar.setCollapsed(true);
                    log("Hover: Auto-collapsed");
                }
            });
        }

        private void toggleIcons(List<SidebarItem<String>> items, boolean show) {
            for (var item : items) {
                if (show) {
                    if (item.getGraphic() == null) {
                        item.setGraphic(new FontIcon(randomIcon()));
                    }
                } else {
                    item.setGraphic(null);
                }

                if (item instanceof SidebarGroup<String> group) {
                    toggleIcons(List.copyOf(group.getItems()), show);
                }

                item.fireEvent(new SidebarItemEvent<>(SidebarItemEvent.itemChanged(), item));
            }
        }

        private void selectRandomVisibleItem() {
            var visibleLeafs = new ArrayList<SidebarItem<String>>();

            for (var item : sidebar.getItems()) {
                collectVisibleLeafs(item, visibleLeafs);
            }

            if (visibleLeafs.isEmpty()) {
                log("Select visible: No visible leaf items found");
                return;
            }

            var target = visibleLeafs.get(RANDOM.nextInt(visibleLeafs.size()));
            log("Jumping to visible item: " + target.getText());

            sidebar.getSelectionModel().select(target);
            sidebar.scrollTo(target);
        }

        private void collectVisibleLeafs(SidebarItem<String> item,
                                         List<SidebarItem<String>> accumulator) {
            if (item instanceof SidebarNav<String> nav) {
                accumulator.add(nav);
            } else if (item instanceof SidebarGroup<String> group) {
                if (group.isExpanded() && !sidebar.isCollapsed()) {
                    for (var child : group.getItems()) {
                        collectVisibleLeafs(child, accumulator);
                    }
                }
            }
        }

        private void selectRandomHiddenItem() {
            var hiddenLeafs = new ArrayList<SidebarItem<String>>();

            for (var item : sidebar.getItems()) {
                collectHiddenLeafs(item, false, hiddenLeafs);
            }

            if (hiddenLeafs.isEmpty()) {
                log("Select hidden: No hidden leaf items found");
                return;
            }

            var target = hiddenLeafs.get(RANDOM.nextInt(hiddenLeafs.size()));
            log("Jumping to hidden item: " + target.getText());

            sidebar.getSelectionModel().select(target);
            sidebar.scrollTo(target);
        }

        private void collectHiddenLeafs(SidebarItem<String> item,
                                        boolean parentCollapsed,
                                        List<SidebarItem<String>> accumulator) {
            if (item instanceof SidebarNav<String> nav) {
                if (parentCollapsed) {
                    accumulator.add(nav);
                }
            } else if (item instanceof SidebarGroup<String> group) {
                boolean collapsed = parentCollapsed || !group.isExpanded() || sidebar.isCollapsed();
                for (var child : group.getItems()) {
                    collectHiddenLeafs(child, collapsed, accumulator);
                }
            }
        }

        private void populateManyItems() {
            sidebar.getItems().clear();

            for (int i = 1; i <= 5; i++) {
                var section = new SidebarSection<String>(FAKER.job().title(), randomAccessory());
                sidebar.getItems().add(section);

                for (int j = 1; j <= 8; j++) {
                    if (j % 3 == 0) {
                        var group = new SidebarGroup<String>(
                            FAKER.commerce().department(), new FontIcon(randomIcon()), randomAccessory()
                        );

                        for (int k = 1; k <= 4; k++) {
                            var nav = new SidebarNav<String>(
                                FAKER.commerce().productName(), new FontIcon(randomIcon()), randomAccessory()
                            );
                            group.getItems().add(nav);
                        }
                        sidebar.getItems().add(group);
                    } else {
                        var nav = new SidebarNav<String>(
                            FAKER.commerce().department(), new FontIcon(randomIcon()), randomAccessory()
                        );
                        sidebar.getItems().add(nav);
                    }
                }
            }
        }

        private @Nullable Node randomAccessory() {
            int type = RANDOM.nextInt(4);
            return switch (type) {
                case 1 -> createBadge(String.valueOf(RANDOM.nextInt(99) + 1));
                case 2 -> {
                    var menu = new MenuButton();
                    menu.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
                    menu.setGraphic(new FontIcon(randomIcon()));
                    menu.getItems().addAll(
                        new MenuItem("Action 1"),
                        new MenuItem("Action 2")
                    );
                    yield menu;
                }
                case 3 -> {
                    var button = new Button();
                    button.getStyleClass().addAll(Styles.FLAT);
                    button.setGraphic(new FontIcon(randomIcon()));
                    yield button;
                }
                default -> null;
            };
        }

        private void addItemToRandomGroup() {
            var groups = new ArrayList<SidebarGroup<String>>();
            for (var item : sidebar.getItems()) {
                if (item instanceof SidebarGroup<String> group) {
                    groups.add(group);
                }
            }

            if (groups.isEmpty()) {
                log("No groups available to add child items");
                return;
            }

            var targetGroup = groups.get(RANDOM.nextInt(groups.size()));
            var nav = new SidebarNav<String>("Dynamic Item " + counter++, new FontIcon(randomIcon()));
            targetGroup.getItems().add(nav);
            log("Added " + nav.getText() + " to " + targetGroup.getText());
        }

        private void removeRandomItem() {
            if (sidebar.getItems().isEmpty()) {
                return;
            }

            int idx = RANDOM.nextInt(sidebar.getItems().size());
            var item = sidebar.getItems().get(idx);

            if (item instanceof SidebarGroup<String> g && !g.getItems().isEmpty() && RANDOM.nextBoolean()) {
                var removedSub = g.getItems().remove(RANDOM.nextInt(g.getItems().size()));
                log("Removed " + removedSub.getText() + " from " + g.getText());
            } else {
                sidebar.getItems().remove(idx);
                log("Removed top-level: " + item.getText());
            }
        }

        private void mutateRandomItem() {
            var allItems = collectFlatItems();
            if (allItems.isEmpty()) {
                return;
            }

            var target = allItems.get(RANDOM.nextInt(allItems.size()));
            target.setText(target.getText() + " [Updated]");

            if (RANDOM.nextBoolean()) {
                target.setGraphic(new FontIcon(randomIcon()));
            } else {
                target.setAccessory(createBadge("NEW"));
            }

            target.fireEvent(new SidebarItemEvent<>(SidebarItemEvent.itemChanged(), target));
            log("Mutated: " + target.getText());
        }

        private List<SidebarItem<String>> collectFlatItems() {
            var list = new ArrayList<SidebarItem<String>>();
            for (var item : sidebar.getItems()) {
                list.add(item);
                if (item instanceof SidebarGroup<String> group) {
                    list.addAll(group.getItems());
                }
            }
            return list;
        }

        private void log(String msg) {
            logArea.appendText(msg + "\n");
        }

        private static Node createBadge(String text) {
            var label = new Label(text);
            label.getStyleClass().add("badge-text");

            var badge = new StackPane(label);
            badge.getStyleClass().add("badge");
            return badge;
        }

        public enum ContainerType {
            BORDER_PANE("BorderPane"),
            HBOX("HBox"),
            ANCHOR_PANE("AnchorPane"),
            STACK_PANE("StackPane");

            private final String title;

            ContainerType(String title) {
                this.title = title;
            }

            @Override
            public String toString() {
                return title;
            }
        }

        public static class ContentPane extends StackPane {

            private final Sidebar<String> sidebar;
            private final VBox centerPane;
            private @Nullable Pane currentContainer;

            public ContentPane(Sidebar<String> sidebar, TextArea logArea) {
                this.sidebar = sidebar;
                this.centerPane = new VBox(10, new Label("Actions Log"), logArea);
                this.centerPane.setPadding(new Insets(0, 15, 0, 15));

                switchContainer(ContainerType.BORDER_PANE);
            }

            public void switchContainer(ContainerType type) {
                getChildren().clear();
                if (currentContainer != null) {
                    currentContainer.getChildren().clear();
                }

                AnchorPane.clearConstraints(sidebar);
                AnchorPane.clearConstraints(centerPane);
                HBox.setHgrow(centerPane, Priority.NEVER);
                HBox.setHgrow(sidebar, Priority.NEVER);
                VBox.setVgrow(sidebar, Priority.ALWAYS);
                sidebar.setMaxHeight(Double.MAX_VALUE);

                switch (type) {
                    case BORDER_PANE -> {
                        var pane = new BorderPane();
                        pane.setLeft(sidebar);
                        pane.setCenter(centerPane);
                        currentContainer = pane;
                    }
                    case HBOX -> {
                        var pane = new HBox();
                        HBox.setHgrow(centerPane, Priority.ALWAYS);
                        pane.getChildren().addAll(sidebar, centerPane);
                        currentContainer = pane;
                    }
                    case ANCHOR_PANE -> {
                        var pane = new AnchorPane();
                        var contentBox = new HBox(sidebar, centerPane);
                        HBox.setHgrow(centerPane, Priority.ALWAYS);

                        AnchorPane.setTopAnchor(contentBox, 0.0);
                        AnchorPane.setBottomAnchor(contentBox, 0.0);
                        AnchorPane.setLeftAnchor(contentBox, 0.0);
                        AnchorPane.setRightAnchor(contentBox, 0.0);

                        pane.getChildren().add(contentBox);
                        currentContainer = pane;
                    }
                    case STACK_PANE -> {
                        var pane = new StackPane();
                        var contentBox = new HBox(sidebar, centerPane);
                        HBox.setHgrow(centerPane, Priority.ALWAYS);

                        pane.getChildren().add(contentBox);
                        currentContainer = pane;
                    }
                }

                getChildren().add(currentContainer);
            }
        }

        public class ControlPane extends FlowPane {

            public ControlPane(Playground parent) {
                super(10, 10);
                setAlignment(Pos.CENTER_LEFT);

                var collapseBtn = new Button("Toggle Collapse");
                collapseBtn.setOnAction(_ -> parent.sidebar.setCollapsed(!parent.sidebar.isCollapsed()));

                var widthSpin = new Spinner<@Nullable Integer>(100, 500, (int) parent.sidebar.getPrefWidth(), 10);
                widthSpin.setEditable(true);
                widthSpin.setPrefWidth(90);
                widthSpin.valueProperty().subscribe(val -> {
                    if (val != null) {
                        parent.sidebar.setPrefWidth(val);
                        parent.log("Pref width changed: " + val);
                    }
                });

                var widthBox = new HBox(5, new Label("Width:"), widthSpin);
                widthBox.setAlignment(Pos.CENTER_LEFT);

                var containerCombo = new ComboBox<@Nullable ContainerType>();
                containerCombo.getItems().addAll(ContainerType.values());
                containerCombo.setValue(ContainerType.BORDER_PANE);
                containerCombo.valueProperty().addListener((_, _, val) -> {
                    if (val != null) {
                        parent.contentPane.switchContainer(val);
                        parent.log("Container switched to: " + val);
                    }
                });

                var containerBox = new HBox(5, new Label("Container:"), containerCombo);
                containerBox.setAlignment(Pos.CENTER_LEFT);

                var hoverCheckItem = new CheckMenuItem("Expand on Hover");
                hoverCheckItem.setSelected(parent.hoverExpandEnabled);
                hoverCheckItem.selectedProperty().addListener((_, _, val) -> {
                    parent.hoverExpandEnabled = val;
                    parent.log("Hover expansion: " + (val ? "On" : "Off"));
                });

                var toggleIconsItem = new CheckMenuItem("Toggle Icons");
                toggleIconsItem.setSelected(true);
                toggleIconsItem.setOnAction(_ -> {
                    boolean show = toggleIconsItem.isSelected();
                    parent.toggleIcons(parent.sidebar.getItems(), show);
                    parent.log("Icons: " + (show ? "Show" : "Hide"));
                });

                var toggleHeaderItem = new CheckMenuItem("Show Header");
                toggleHeaderItem.setSelected(parent.sidebar.getHeader() != null);
                toggleHeaderItem.setOnAction(_ -> {
                    boolean show = toggleHeaderItem.isSelected();
                    parent.sidebar.setHeader(show ? parent.defaultHeader : null);
                    parent.log("Header: " + (show ? "Show" : "Hide"));
                });

                var toggleFooterItem = new CheckMenuItem("Show Footer");
                toggleFooterItem.setSelected(parent.sidebar.getFooter() != null);
                toggleFooterItem.setOnAction(_ -> {
                    boolean show = toggleFooterItem.isSelected();
                    parent.sidebar.setFooter(show ? parent.defaultFooter : null);
                    parent.log("Footer: " + (show ? "Show" : "Hide"));
                });

                var addGroupItem = new MenuItem("Add Group Item");
                addGroupItem.setOnAction(_ -> {
                    var group = new SidebarGroup<String>("Group " + parent.counter++, new FontIcon(randomIcon()));
                    parent.sidebar.getItems().add(group);
                    parent.log("Added: " + group.getText());
                });

                var addChildItem = new MenuItem("Add to Random Group");
                addChildItem.setOnAction(_ -> parent.addItemToRandomGroup());

                var removeRandomItem = new MenuItem("Remove Random Item");
                removeRandomItem.setOnAction(_ -> parent.removeRandomItem());

                var populateItem = new MenuItem("Generate 50 Random Items");
                populateItem.setOnAction(_ -> parent.populateManyItems());

                var mutateRandomItem = new MenuItem("Mutate Random Item");
                mutateRandomItem.setOnAction(_ -> parent.mutateRandomItem());

                var clearItem = new MenuItem("Clear All");
                clearItem.setOnAction(_ -> parent.sidebar.getItems().clear());

                var selectVisibleItem = new MenuItem("Select Random Visible");
                selectVisibleItem.setOnAction(_ -> parent.selectRandomVisibleItem());

                var selectHiddenItem = new MenuItem("Select Random Hidden");
                selectHiddenItem.setOnAction(_ -> parent.selectRandomHiddenItem());

                var actionsMenu = new MenuButton("Actions");
                actionsMenu.getItems().addAll(
                    hoverCheckItem,
                    toggleIconsItem,
                    new SeparatorMenuItem(),
                    toggleHeaderItem, toggleFooterItem,
                    new SeparatorMenuItem(),
                    addGroupItem, addChildItem, removeRandomItem, populateItem, mutateRandomItem, clearItem,
                    new SeparatorMenuItem(),
                    selectVisibleItem, selectHiddenItem
                );

                getChildren().addAll(
                    collapseBtn,
                    widthBox,
                    containerBox,
                    actionsMenu
                );
            }
        }
    }
}
