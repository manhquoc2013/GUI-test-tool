/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import atlantafx.base.util.Disposable;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static javafx.scene.control.ScrollPane.ScrollBarPolicy;

/**
 * The default skin for the {@link Sidebar} control.
 */
public class SidebarSkin<T extends @Nullable Object> extends SkinBase<Sidebar<T>> {

    private final SidebarBehavior<@Nullable T> behavior;
    private final SidebarRootPane<@Nullable T> rootPane;
    private final ScrollPane scrollPane;
    private final SidebarContentPane contentPane;
    private @Nullable Timeline animationTimeline;
    private final DoubleProperty animatedWidth = new SimpleDoubleProperty(this, "animatedWidth", -1);

    private final ChangeListener<SidebarHeader<@Nullable T>> headerListener;
    private final ChangeListener<SidebarFooter<@Nullable T>> footerListener;
    private final ChangeListener<Boolean> collapsedListener;
    private final ChangeListener<Number> prefWidthListener;
    private final ListChangeListener<SidebarItem<@Nullable T>> itemsListener;

    private final List<SidebarItemCell<@Nullable T>> interactiveCellsCache = new ArrayList<>();
    private boolean interactiveCellsDirty = true;

    public SidebarSkin(Sidebar<@Nullable T> control) {
        super(control);
        this.behavior = new SidebarBehavior<>(control);

        contentPane = new SidebarContentPane();
        contentPane.getStyleClass().add("content");

        scrollPane = new ScrollPane(contentPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        rootPane = new SidebarRootPane<>(control, scrollPane, this);
        rootPane.getStyleClass().add("container");

        getChildren().add(rootPane);

        animatedWidth.addListener((_, _, _) -> control.requestLayout());

        this.collapsedListener = (_, _, isCollapsed) -> {
            markInteractiveCellsDirty();
            animateState(isCollapsed);
        };
        this.headerListener = (_, _, _) -> {
            rootPane.updateHeader();
            markInteractiveCellsDirty();
            notifyLayoutBoundsChanged();
        };
        this.footerListener = (_, _, _) -> {
            rootPane.updateFooter();
            markInteractiveCellsDirty();
            notifyLayoutBoundsChanged();
        };
        this.prefWidthListener = (_, _, _) -> notifyLayoutBoundsChanged();

        this.itemsListener = change -> {
            boolean hasChanges = false;
            while (change.next()) {
                if (change.wasPermutated()) {
                    rebuildContent();
                    hasChanges = true;
                    break;
                } else {
                    if (change.wasRemoved()) {
                        for (int i = 0; i < change.getRemovedSize(); i++) {
                            int removeIdx = change.getFrom();
                            if (removeIdx < contentPane.getChildren().size()) {
                                Node node = contentPane.getChildren().remove(removeIdx);
                                disposeNode(node);
                            }
                        }
                        hasChanges = true;
                    }
                    if (change.wasAdded()) {
                        int addIdx = change.getFrom();
                        for (SidebarItem<T> addedItem : change.getAddedSubList()) {
                            Node node = createNodeFor(addedItem, addIdx == 0);
                            contentPane.getChildren().add(addIdx++, node);
                        }
                        hasChanges = true;
                    }
                }
            }
            if (hasChanges) {
                markInteractiveCellsDirty();
                notifyLayoutBoundsChanged();
            }
        };

        control.collapsedProperty().addListener(collapsedListener);
        control.prefWidthProperty().addListener(prefWidthListener);
        control.headerProperty().addListener(headerListener);
        control.footerProperty().addListener(footerListener);
        control.getItems().addListener(itemsListener);

        rebuildContent();
    }

    public SidebarBehavior<T> getBehavior() {
        return behavior;
    }

    public void scrollTo(@Nullable SidebarItem<@Nullable T> item) {
        if (item == null) {
            return;
        }

        var group = item.getParentGroup();
        while (group != null) {
            if (!group.isExpanded()) {
                group.setExpanded(true);
            }
            group = group.getParentGroup();
        }

        var cell = findCellForItem(item);
        if (cell != null) {
            scrollTo(cell);
        }
    }

    public void scrollTo(@Nullable SidebarItemCell<@Nullable T> cell) {
        if (cell == null) {
            return;
        }

        Node current = cell;
        boolean insideScroll = false;
        while (current != null && current != rootPane) {
            if (current == contentPane) {
                insideScroll = true;
                break;
            }
            current = current.getParent();
        }

        if (!insideScroll) {
            return;
        }

        var cellBoundsInScene = cell.localToScene(cell.getBoundsInLocal());
        if (cellBoundsInScene == null) {
            return;
        }

        var cellBoundsInContent = contentPane.sceneToLocal(cellBoundsInScene);
        if (cellBoundsInContent == null) {
            return;
        }

        double cellMinY = cellBoundsInContent.getMinY();
        double cellMaxY = cellBoundsInContent.getMaxY();

        var viewportBounds = scrollPane.getViewportBounds();
        double viewportHeight = viewportBounds.getHeight();
        double contentHeight = contentPane.getHeight();

        if (contentHeight <= viewportHeight) {
            return;
        }

        double scrollableHeight = contentHeight - viewportHeight;
        if (scrollableHeight <= 0) {
            return;
        }

        double currentVvalue = scrollPane.getVvalue();
        double currentScrollY = currentVvalue * scrollableHeight;

        double targetScrollY = currentScrollY;

        if (cellMinY < currentScrollY) {
            targetScrollY = cellMinY;
        } else if (cellMaxY > currentScrollY + viewportHeight) {
            targetScrollY = cellMaxY - viewportHeight;
        }

        if (targetScrollY != currentScrollY) {
            double newVvalue = Math.max(0.0, Math.min(1.0, targetScrollY / scrollableHeight));
            scrollPane.setVvalue(newVvalue);
        }
    }

    public List<SidebarItemCell<T>> getInteractiveCells() {
        if (interactiveCellsDirty) {
            interactiveCellsCache.clear();

            if (rootPane.headerCell != null) {
                interactiveCellsCache.add(rootPane.headerCell.cell);
            }

            collectInteractiveCells(contentPane, interactiveCellsCache);

            if (rootPane.footerCell != null) {
                interactiveCellsCache.add(rootPane.footerCell.cell);
            }

            interactiveCellsDirty = false;
        }

        return Collections.unmodifiableList(interactiveCellsCache);
    }

    public static double computeNodeWidth(@Nullable Node node) {
        if (node == null) {
            return 0.0;
        }

        if (node instanceof Region region) {
            double w = region.prefWidth(-1);
            if (w > 0) {
                return w;
            }
        }

        double w = node.prefWidth(-1);
        if (w <= 0) {
            w = node.getBoundsInLocal().getWidth();
        }
        return w;
    }

    public void notifyLayoutBoundsChanged() {
        if (animationTimeline == null || animationTimeline.getStatus() != Animation.Status.RUNNING) {
            animatedWidth.set(-1);
        }
        getSkinnable().requestLayout();
    }

    @Override
    @SuppressWarnings("ConstantValue")
    public void dispose() {
        var control = getSkinnable();
        if (control != null) {
            control.collapsedProperty().removeListener(collapsedListener);
            control.prefWidthProperty().removeListener(prefWidthListener);
            control.headerProperty().removeListener(headerListener);
            control.footerProperty().removeListener(footerListener);
            control.getItems().removeListener(itemsListener);
        }

        if (behavior != null) {
            behavior.dispose();
        }

        if (rootPane != null) {
            if (rootPane.headerCell != null) {
                rootPane.headerCell.dispose();
            }
            if (rootPane.footerCell != null) {
                rootPane.footerCell.dispose();
            }
        }

        if (animationTimeline != null) {
            animationTimeline.stop();
        }

        disposeContent();
        interactiveCellsCache.clear();

        super.dispose();
    }

    //*************************************************************************

    // VisibleForTesting
    @Nullable SidebarItemCell<@Nullable T> findCellForItem(SidebarItem<@Nullable T> item) {
        for (var cell : getInteractiveCells()) {
            if (cell.getItem() == item) {
                return cell;
            }
        }
        return null;
    }

    private void markInteractiveCellsDirty() {
        this.interactiveCellsDirty = true;
    }

    private void disposeContent() {
        for (var node : contentPane.getChildren()) {
            disposeNode(node);
        }
        contentPane.getChildren().clear();
    }

    private void disposeNode(Node node) {
        if (node instanceof Disposable disposable) {
            disposable.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    private void collectInteractiveCells(Pane container, List<SidebarItemCell<T>> accumulator) {
        for (var child : container.getChildren()) {
            if (child instanceof SidebarGroupCell<?> groupCell) {
                accumulator.add((SidebarItemCell<T>) groupCell.itemCell);

                if (groupCell.item instanceof SidebarGroup<?> group) {
                    if (group.isExpanded() && !getSkinnable().isCollapsed() && groupCell.itemsPane != null) {
                        collectInteractiveCells(groupCell.itemsPane, accumulator);
                    }
                }
            }
        }
    }

    public double computeContentPrefWidth() {
        // if the control has an explicitly set preferred width, return it
        double explicitPrefWidth = getSkinnable().getPrefWidth();
        if (explicitPrefWidth != Region.USE_COMPUTED_SIZE) {
            return explicitPrefWidth;
        }

        // otherwise, calculate the width based on the content
        double maxW = 0.0;

        if (rootPane.headerCell != null) {
            maxW = Math.max(maxW, rootPane.headerCell.prefWidth(-1));
        }

        double contentW = contentPane.prefWidth(-1);
        maxW = Math.max(maxW, contentW);

        if (rootPane.footerCell != null) {
            maxW = Math.max(maxW, rootPane.footerCell.prefWidth(-1));
        }

        return Math.max(maxW, getSkinnable().getCollapsedWidth());
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double currentAnimW = animatedWidth.get();
        if (currentAnimW >= 0) {
            return snapSizeX(currentAnimW + leftInset + rightInset);
        }

        var sidebar = getSkinnable();
        double w = sidebar.isCollapsed() ? sidebar.getCollapsedWidth() : computeContentPrefWidth();

        return snapSizeX(w + leftInset + rightInset);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double currentAnimW = animatedWidth.get();
        if (currentAnimW >= 0) {
            return snapSizeX(currentAnimW + leftInset + rightInset);
        }

        var sidebar = getSkinnable();
        double w = sidebar.isCollapsed() ? sidebar.getCollapsedWidth() : computeContentPrefWidth();

        return snapSizeX(w + leftInset + rightInset);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double currentAnimW = animatedWidth.get();
        if (currentAnimW >= 0) {
            return snapSizeX(currentAnimW + leftInset + rightInset);
        }

        var sidebar = getSkinnable();
        if (sidebar.isCollapsed()) {
            return snapSizeX(sidebar.getCollapsedWidth() + leftInset + rightInset);
        }

        return super.computeMaxWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    private Node createNodeFor(SidebarItem<T> item, boolean isFirst) {
        if (item instanceof SidebarSection<T> section) {
            return new SidebarSectionCell<>(section, isFirst, getSkinnable());
        } else {
            return new SidebarGroupCell<>(item, 0, getSkinnable(), this);
        }
    }

    private void rebuildContent() {
        disposeContent();
        var items = getSkinnable().getItems();

        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var node = createNodeFor(item, i == 0);
            contentPane.getChildren().add(node);
        }
        markInteractiveCellsDirty();
    }

    private void animateState(boolean collapse) {
        if (animationTimeline != null) {
            animationTimeline.stop();
        }

        var sidebar = getSkinnable();

        // if animatedWidth is not set yet (-1), take the current actual width from getWidth(),
        // so we start strictly from current width, rather than from the computed content prefWidth.
        double currentActualWidth = rootPane.getWidth() > 0 ? rootPane.getWidth() : sidebar.getWidth();

        double animW = animatedWidth.get();
        if (animW < 0) {
            if (currentActualWidth > 0) {
                animW = currentActualWidth;
            } else {
                animW = collapse ? computeContentPrefWidth() : sidebar.getCollapsedWidth();
            }
        }

        double startW = animW;
        double targetW = collapse ? sidebar.getCollapsedWidth() : computeContentPrefWidth();

        animatedWidth.set(startW);

        animationTimeline = new Timeline(
            new KeyFrame(sidebar.getAnimationDuration(), new KeyValue(animatedWidth, targetW))
        );

        animationTimeline.setOnFinished(_ -> notifyLayoutBoundsChanged());
        animationTimeline.play();
    }

    //region SidebarRootPane
    //*************************************************************************

    private static class SidebarRootPane<T extends @Nullable Object> extends Pane {

        private final class CellWrapper<C extends SidebarItemCell<T>>
            extends StackPane implements Disposable {

            private final C cell;

            public CellWrapper(C cell) {
                super(cell);
                this.cell = cell;
            }

            @Override
            public void dispose() {
                cell.dispose();
            }
        }

        private final Sidebar<@Nullable T> sidebar;
        private final ScrollPane scrollPane;
        private final SidebarSkin<@Nullable T> skin;
        private @Nullable CellWrapper<SidebarItemCell<@Nullable T>> headerCell;
        private @Nullable CellWrapper<SidebarItemCell<@Nullable T>> footerCell;
        private final Rectangle sidebarClip = new Rectangle();

        private @Nullable EventHandler<MouseEvent> sceneMouseFilter;
        private long lastFooterMenuHideTime = 0;

        public SidebarRootPane(Sidebar<T> sidebar, ScrollPane scrollPane, SidebarSkin<T> skin) {
            this.sidebar = sidebar;
            this.scrollPane = scrollPane;
            this.skin = skin;

            setClip(sidebarClip);
            getChildren().add(scrollPane);

            updateHeader();
            updateFooter();
        }

        public void updateHeader() {
            if (headerCell != null) {
                headerCell.dispose();
                getChildren().remove(headerCell);
                headerCell = null;
            }

            var header = sidebar.getHeader();
            if (header != null) {
                var cell = new SidebarItemCell<>(header, 0, sidebar, skin);
                headerCell = new CellWrapper<>(cell);
                headerCell.getStyleClass().add("sidebar-header");
                getChildren().add(headerCell);
            }
        }

        public void updateFooter() {
            if (footerCell != null) {
                footerCell.dispose();
                getChildren().remove(footerCell);
                footerCell = null;
            }

            var footer = sidebar.getFooter();
            if (footer != null) {
                var cell = new SidebarItemCell<>(footer, 0, sidebar, skin);
                footerCell = new CellWrapper<>(cell);
                footerCell.getStyleClass().add("sidebar-footer");

                footerCell.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                        ContextMenu cm = sidebar.getFooterMenu();
                        if (cm != null && !cm.getItems().isEmpty()) {
                            showFooterMenu(footerCell, cm);
                            e.consume();
                        }
                    }
                });

                getChildren().add(footerCell);
            }
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();

            sidebarClip.setWidth(w);
            sidebarClip.setHeight(h);

            double y = 0.0;

            double headerH = (headerCell != null) ? snapSizeY(headerCell.prefHeight(w)) : 0.0;
            if (headerCell != null) {
                headerCell.setVisible(true);
                headerCell.resizeRelocate(0, 0, snapSizeX(w), headerH);
                y = snapPositionY(y + headerH);
            }

            double footerH = (footerCell != null) ? snapSizeY(footerCell.prefHeight(w)) : 0.0;
            double scrollH = snapSizeY(Math.max(0, h - y - footerH));

            scrollPane.resizeRelocate(0, y, snapSizeX(w), scrollH);

            if (footerCell != null) {
                double footerY = snapPositionY(h - footerH);
                footerCell.resizeRelocate(0, footerY, snapSizeX(w), footerH);
            }
        }

        private void showFooterMenu(Region cell, ContextMenu contextMenu) {
            // ignore click if < 250 ms since footer closed
            if (System.currentTimeMillis() - lastFooterMenuHideTime < 250) {
                return;
            }

            if (contextMenu.isShowing()) {
                contextMenu.hide();
                return;
            }

            contextMenu.setAutoHide(true);
            contextMenu.setHideOnEscape(true);

            contextMenu.setOnShowing(_ -> {
                if (!sidebar.isCollapsed()) {
                    contextMenu.setMinWidth(cell.getWidth());
                } else {
                    contextMenu.setMinWidth(Region.USE_COMPUTED_SIZE);
                }
            });

            contextMenu.show(cell, Side.TOP, 2.0, -2.0);

            var scene = cell.getScene();
            if (scene != null) {
                if (sceneMouseFilter != null) {
                    scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMouseFilter);
                }

                sceneMouseFilter = event -> {
                    if (!contextMenu.isShowing()) {
                        return;
                    }

                    Node menu = contextMenu.getSkin() != null ? contextMenu.getSkin().getNode() : null;
                    if (event.getTarget() instanceof Node n) {
                        // ignore clicks inside the menu itself
                        if (menu != null && isDescendantOf(n, menu)) {
                            return;
                        }

                        // store the close time if footer cell was clicked
                        if (isDescendantOf(n, cell)) {
                            lastFooterMenuHideTime = System.currentTimeMillis();
                        }
                    }

                    // close on any outside click
                    contextMenu.hide();
                };

                scene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneMouseFilter);
            }

            contextMenu.setOnHiding(_ -> {
                if (scene != null && sceneMouseFilter != null) {
                    scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMouseFilter);
                    sceneMouseFilter = null;
                }
            });
        }

        private static boolean isDescendantOf(Node node, Node parent) {
            Node current = node;
            while (current != null) {
                if (current == parent) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }
    }
    //endregion

    //region SidebarContentPane
    //*************************************************************************

    private static class SidebarContentPane extends Pane {

        @Override
        protected double computePrefWidth(double height) {
            double maxW = 0.0;
            for (var child : getChildren()) {
                maxW = Math.max(maxW, child.prefWidth(-1));
            }
            return snapSizeX(maxW);
        }

        @Override
        protected double computePrefHeight(double width) {
            double totalH = 0.0;
            double targetW = width > 0 ? width : getWidth();
            for (var child : getChildren()) {
                totalH += snapSizeY(child.prefHeight(targetW));
            }
            return snapSizeY(totalH);
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double y = 0.0;
            for (var child : getChildren()) {
                double childH = snapSizeY(child.prefHeight(w));
                child.resizeRelocate(0, y, snapSizeX(w), childH);
                y = snapPositionY(y + childH);
            }
        }
    }
    //endregion

    //region SidebarSectionCell
    //*************************************************************************

    private static class SidebarSectionCell<T extends @Nullable Object>
        extends Region implements Disposable {

        private final Sidebar<T> sidebar;
        private final SidebarSection<T> section;
        private final Label titleLabel = new Label();
        private final StackPane accessoryPane = new StackPane();
        private final EventHandler<SidebarItemEvent<T>> itemChangeHandler;

        public SidebarSectionCell(SidebarSection<T> section, boolean isFirst, Sidebar<T> sidebar) {
            this.sidebar = sidebar;
            this.section = section;

            getStyleClass().add("sidebar-section");
            if (isFirst) {
                getStyleClass().add("first");
            }

            accessoryPane.getStyleClass().add("accessory");

            titleLabel.setText(section.getText() != null ? section.getText().toUpperCase() : "");
            getChildren().addAll(titleLabel, accessoryPane);

            this.itemChangeHandler = _ -> {
                titleLabel.setText(section.getText().toUpperCase());
                updateAccessory();

                if (sidebar.getSkin() instanceof SidebarSkin<?> skin) {
                    skin.notifyLayoutBoundsChanged();
                }
            };
            section.addEventHandler(SidebarItemEvent.itemChanged(), itemChangeHandler);

            updateAccessory();
        }

        @Override
        public void dispose() {
            section.removeEventHandler(SidebarItemEvent.itemChanged(), itemChangeHandler);
        }

        @Override
        protected double computePrefWidth(double height) {
            double labelW = snapSizeX(titleLabel.prefWidth(-1));
            double accessoryW = computeNodeWidth(section.getAccessory());
            double gap = accessoryW > 0 ? snapSpaceX(sidebar.getCellSpacing()) : 0;

            Insets cellPadding = sidebar.getCellPadding();
            Insets padding = getPadding();

            double leftPadding = snapSpaceX(cellPadding.getLeft() + padding.getLeft());
            double rightPadding = snapSpaceX(cellPadding.getRight() + padding.getRight());

            return snapSizeX(leftPadding + labelW + gap + accessoryW + rightPadding);
        }

        @Override
        protected double computePrefHeight(double width) {
            double labelH = snapSizeY(titleLabel.prefHeight(-1));
            double accessoryH = snapSizeY(accessoryPane.prefHeight(-1));
            double maxContentH = Math.max(labelH, accessoryH);

            Insets cellPadding = sidebar.getCellPadding();
            Insets padding = getPadding();
            double yPadding = cellPadding.getTop() + cellPadding.getBottom()
                + padding.getTop() + padding.getBottom();

            return snapSizeY(yPadding + maxContentH);
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            boolean isCollapsed = sidebar.isCollapsed();
            titleLabel.setVisible(!isCollapsed);
            accessoryPane.setVisible(!isCollapsed);

            if (isCollapsed) {
                return;
            }

            double w = getWidth();
            double h = getHeight();

            Insets cellPadding = sidebar.getCellPadding();
            Insets padding = getPadding();

            double leftPadding = snapSpaceX(cellPadding.getLeft() + padding.getLeft());
            double rightPadding = snapSpaceX(cellPadding.getRight() + padding.getRight());
            double topPadding = snapSpaceY(cellPadding.getTop() + padding.getTop());
            double bottomPadding = snapSpaceY(cellPadding.getBottom() + padding.getBottom());
            double contentAreaHeight = h - topPadding - bottomPadding;

            @SuppressWarnings("UnnecessaryLocalVariable")
            double currentX = leftPadding;
            double accessoryW = snapSizeX(accessoryPane.prefWidth(-1));
            double accessoryH = snapSizeY(accessoryPane.prefHeight(-1));

            if (accessoryW > 0) {
                double accessoryX = snapPositionX(w - rightPadding - accessoryW);
                double accessoryY = snapPositionY(topPadding + (contentAreaHeight - accessoryH) / 2.0);
                accessoryPane.resizeRelocate(accessoryX, accessoryY, accessoryW, accessoryH);
            }

            double labelH = snapSizeY(titleLabel.prefHeight(-1));
            double labelY = snapPositionY(topPadding + (contentAreaHeight - labelH) / 2.0);
            double gap = accessoryW > 0 ? snapSpaceX(sidebar.getCellSpacing()) : 0;
            double maxLabelWidth = snapSizeX(w - rightPadding - accessoryW - gap - currentX);

            titleLabel.resizeRelocate(currentX, labelY, Math.max(0, maxLabelWidth), labelH);
        }

        private void updateAccessory() {
            accessoryPane.getChildren().clear();
            if (section.getAccessory() != null) {
                accessoryPane.getChildren().add(section.getAccessory());
            }
        }
    }
    //endregion

    //region SidebarGroupCell
    //*************************************************************************

    private static class SidebarGroupCell<T extends @Nullable Object>
        extends Region implements Disposable {

        private final Sidebar<@Nullable T> sidebar;
        private final SidebarItem<@Nullable T> item;
        private final SidebarItemCell<@Nullable T> itemCell;
        private final @Nullable SidebarGroupItemsPane<@Nullable T> itemsPane;

        public SidebarGroupCell(SidebarItem<T> item, int level,
                                Sidebar<T> sidebar, SidebarSkin<T> skin) {
            this.item = item;
            this.sidebar = sidebar;
            getStyleClass().add("sidebar-menu");

            this.itemCell = new SidebarItemCell<>(item, level, sidebar, skin);
            getChildren().add(itemCell);

            if (item instanceof SidebarGroup<T> group) {
                this.itemsPane = new SidebarGroupItemsPane<>(group, level + 1, sidebar, skin);
                getChildren().add(itemsPane);
            } else {
                this.itemsPane = null;
            }
        }

        @Override
        protected double computePrefWidth(double height) {
            Insets padding = getPadding();
            double contentH = height > 0 ? height - padding.getTop() - padding.getBottom() : height;
            return snapSizeX(padding.getLeft() + itemCell.prefWidth(contentH) + padding.getRight());
        }

        @Override
        protected double computePrefHeight(double width) {
            Insets padding = getPadding();
            double contentW = width > 0 ? width - padding.getLeft() - padding.getRight() : width;

            double cellH = snapSizeY(itemCell.prefHeight(contentW));
            double itemsH = itemsPane != null && !sidebar.isCollapsed()
                ? snapSizeY(itemsPane.prefHeight(contentW))
                : 0;

            return snapSizeY(padding.getTop() + cellH + itemsH + padding.getBottom());
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            Insets padding = getPadding();
            double top = padding.getTop();
            double left = padding.getLeft();
            double contentW = snapSizeX(getWidth() - left - padding.getRight());

            double cellH = snapSizeY(itemCell.prefHeight(contentW));

            itemCell.resizeRelocate(left, top, contentW, cellH);

            if (itemsPane != null) {
                if (!sidebar.isCollapsed()) {
                    itemsPane.setVisible(true);
                    double itemsH = snapSizeY(itemsPane.prefHeight(contentW));
                    itemsPane.resizeRelocate(left, top + cellH, contentW, itemsH);
                } else {
                    itemsPane.setVisible(false);
                }
            }
        }

        @Override
        public void dispose() {
            itemCell.dispose();
            if (itemsPane != null) {
                itemsPane.dispose();
            }
        }
    }
    //endregion

    //region SidebarGroupItemsPane
    //*************************************************************************

    private static class SidebarGroupItemsPane<T extends @Nullable Object> extends Pane implements Disposable {

        private final Sidebar<T> sidebar;
        private final SidebarSkin<T> skin;
        private final SidebarGroup<T> group;
        private final int level;

        private final Rectangle clipRect = new Rectangle();
        private final DoubleProperty animatedHeight = new SimpleDoubleProperty(0);
        private @Nullable Timeline expandTimeline;

        private final Map<SidebarNav<T>, SidebarGroupCell<T>> navGroupMap = new HashMap<>();
        private final ListChangeListener<SidebarNav<T>> itemsChangeListener;
        private final ChangeListener<Boolean> expandedListener;

        public SidebarGroupItemsPane(SidebarGroup<T> group, int level,
                                     Sidebar<T> sidebar, SidebarSkin<T> skin) {
            this.group = group;
            this.level = level;
            this.sidebar = sidebar;
            this.skin = skin;

            getStyleClass().add("items");
            setClip(clipRect);

            for (var child : group.getItems()) {
                addChild(child);
            }

            this.itemsChangeListener = c -> {
                while (c.next()) {
                    if (c.wasRemoved()) {
                        for (var removed : c.getRemoved()) {
                            removeChild(removed);
                        }
                    }
                    if (c.wasAdded()) {
                        for (var added : c.getAddedSubList()) {
                            addChild(added);
                        }
                    }
                }

                if (group.isExpanded() && !sidebar.isCollapsed()) {
                    animateExpand(true);
                }
                skin.markInteractiveCellsDirty();
            };
            group.getItems().addListener(itemsChangeListener);

            this.expandedListener = (_, _, isExpanded) -> {
                skin.markInteractiveCellsDirty();
                animateExpand(isExpanded);
            };
            group.expandedProperty().addListener(expandedListener);

            if (group.isExpanded() && !sidebar.isCollapsed()) {
                animatedHeight.set(computeCellsHeight());
            }

            animatedHeight.addListener((_, _, newV) -> {
                clipRect.setHeight(snapSizeY(newV.doubleValue()));
                requestLayout();
            });
        }

        @Override
        protected double computePrefWidth(double height) {
            double maxW = 0.0;
            for (var groupCell : navGroupMap.values()) {
                maxW = Math.max(maxW, groupCell.prefWidth(height));
            }
            return snapSizeX(maxW);
        }

        @Override
        protected double computePrefHeight(double width) {
            return snapSizeY(animatedHeight.get());
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double currentY = 0.0;

            for (var nav : group.getItems()) {
                var groupCell = navGroupMap.get(nav);
                if (groupCell != null) {
                    double childH = snapSizeY(groupCell.prefHeight(w));
                    groupCell.resizeRelocate(0, currentY, snapSizeX(w), childH);
                    currentY = snapPositionY(currentY + childH);
                }
            }

            clipRect.setWidth(snapSizeX(w));
            clipRect.setHeight(snapSizeY(animatedHeight.get()));
        }

        @Override
        public void dispose() {
            group.getItems().removeListener(itemsChangeListener);
            group.expandedProperty().removeListener(expandedListener);
            if (expandTimeline != null) {
                expandTimeline.stop();
            }
            for (var node : navGroupMap.values()) {
                node.dispose();
            }
            navGroupMap.clear();
        }

        private void addChild(SidebarNav<T> nav) {
            var groupCell = new SidebarGroupCell<>(nav, level, sidebar, skin);
            navGroupMap.put(nav, groupCell);
            getChildren().add(groupCell);
        }

        private void removeChild(SidebarNav<T> nav) {
            var node = navGroupMap.remove(nav);
            if (node != null) {
                node.dispose();
                getChildren().remove(node);
            }
        }

        private double computeCellsHeight() {
            double totalSubH = 0.0;
            for (var groupCell : navGroupMap.values()) {
                totalSubH += snapSizeY(groupCell.prefHeight(-1));
            }
            return snapSizeY(totalSubH);
        }

        private void animateExpand(boolean expand) {
            if (expandTimeline != null) {
                expandTimeline.stop();
            }

            double cellsH = expand ? computeCellsHeight() : 0.0;
            expandTimeline = new Timeline(
                new KeyFrame(sidebar.getAnimationDuration(), new KeyValue(animatedHeight, cellsH))
            );
            expandTimeline.play();
        }
    }
    //endregion

    //region SidebarItemCell
    //*************************************************************************

    public static class SidebarItemCell<T extends @Nullable Object>
        extends Region implements Disposable {

        private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
        private static final PseudoClass SELECTED_WITHIN_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected-within");
        private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
        private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");

        private final Sidebar<@Nullable T> sidebar;
        private final SidebarItem<@Nullable T> item;
        private final int level;

        private final Label titleLabel = new Label();
        private final StackPane accessoryPane = new StackPane();
        private final StackPane graphicPane = new StackPane();
        private @Nullable SVGPath arrowPath;
        private @Nullable Timeline arrowTimeline;

        private final EventHandler<SidebarItemEvent<@Nullable T>> itemChangeHandler;
        private final ChangeListener<Boolean> selectedListener;
        private final ChangeListener<Boolean> selectedWithinListener;
        private @Nullable ChangeListener<Boolean> expandedListener;
        private @Nullable ListChangeListener<SidebarNav<@Nullable T>> groupItemsListener;

        public SidebarItemCell(SidebarItem<@Nullable T> item, int level,
                               Sidebar<@Nullable T> sidebar, SidebarSkin<@Nullable T> skin) {
            this.item = item;
            this.level = level;
            this.sidebar = sidebar;

            setFocusTraversable(false);
            setPickOnBounds(true);

            titleLabel.setText(item.getText());
            titleLabel.setMouseTransparent(true);

            graphicPane.getStyleClass().add("graphic");
            accessoryPane.getStyleClass().add("accessory");

            getChildren().addAll(titleLabel, accessoryPane, graphicPane);

            updateGraphic();
            updateAccessory();

            addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                    if (e.getTarget() instanceof Node node) {
                        if (SidebarRootPane.isDescendantOf(node, accessoryPane) && item.getAccessory() != null) {
                            return;
                        }
                    }
                    skin.getBehavior().handleItemClick(item, this);
                    e.consume();
                }
            });

            if (item instanceof SidebarGroup<T> group) {
                this.expandedListener = (_, _, isExpanded) -> {
                    pseudoClassStateChanged(EXPANDED_PSEUDO_CLASS, isExpanded);
                    if (arrowPath != null && accessoryPane.getChildren().contains(arrowPath)) {
                        if (arrowTimeline != null) {
                            arrowTimeline.stop();
                        }
                        arrowTimeline = new Timeline(
                            new KeyFrame(Duration.millis(150),
                                new KeyValue(arrowPath.rotateProperty(), isExpanded ? 180 : 0)
                            )
                        );
                        arrowTimeline.play();
                    }
                };
                group.expandedProperty().addListener(expandedListener);

                if (group.isExpanded()) {
                    if (arrowPath != null) {
                        arrowPath.setRotate(180);
                    }
                    pseudoClassStateChanged(EXPANDED_PSEUDO_CLASS, true);
                }

                this.groupItemsListener = _ -> {
                    updateAccessory();
                    skin.notifyLayoutBoundsChanged();
                };
                group.getItems().addListener(groupItemsListener);
            }

            this.itemChangeHandler = _ -> {
                titleLabel.setText(item.getText());
                updateGraphic();
                updateAccessory();

                skin.notifyLayoutBoundsChanged();
            };
            item.addEventHandler(SidebarItemEvent.itemChanged(), itemChangeHandler);

            this.selectedListener = (_, _, _) -> updatePseudoClasses();
            this.selectedWithinListener = (_, _, _) -> updatePseudoClasses();

            item.selectedProperty().addListener(selectedListener);
            item.selectedWithinProperty().addListener(selectedWithinListener);

            // sidebar-cell class is picked from the SidebarItem
            Bindings.bindContent(getStyleClass(), item.getStyleClass());

            updatePseudoClasses();
        }

        public SidebarItem<T> getItem() {
            return item;
        }

        public void setFocusedState(boolean focused) {
            pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, focused);
        }

        @Override
        public void dispose() {
            item.removeEventHandler(SidebarItemEvent.itemChanged(), itemChangeHandler);
            item.selectedProperty().removeListener(selectedListener);
            item.selectedWithinProperty().removeListener(selectedWithinListener);
            Bindings.unbindContent(getStyleClass(), item.getStyleClass());

            if (item instanceof SidebarGroup<T> group) {
                if (expandedListener != null) {
                    group.expandedProperty().removeListener(expandedListener);
                }
                if (groupItemsListener != null) {
                    group.getItems().removeListener(groupItemsListener);
                }
            }

            if (arrowTimeline != null) {
                arrowTimeline.stop();
            }
        }

        @Override
        protected double computePrefWidth(double height) {
            return computeUnconstrainedWidth();
        }

        @Override
        protected double computePrefHeight(double width) {
            Insets padding = sidebar.getCellPadding();
            return snapSizeY(padding.getTop() + sidebar.getCellHeight() + padding.getBottom());
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();

            boolean collapsed = sidebar.isCollapsed();
            Node left = item.getGraphic();

            Insets cellPadding = sidebar.getCellPadding();

            double topPadding = snapSpaceY(cellPadding.getTop());
            double bottomPadding = snapSpaceY(cellPadding.getBottom());
            double contentAreaHeight = h - topPadding - bottomPadding;

            double indent = collapsed
                ? cellPadding.getLeft()
                : cellPadding.getLeft() + (level * sidebar.getIndent());

            double leftIndent = snapSpaceX(indent);
            double currentX = leftIndent;

            double leftW = 0.0;
            if (left != null) {
                graphicPane.setVisible(true);
                leftW = snapSizeX(computeNodeWidth(left));
                double leftH = snapSizeY(
                    left.prefHeight(-1) > 0
                        ? left.prefHeight(-1)
                        : left.getLayoutBounds().getHeight()
                );
                double leftY = snapPositionY(topPadding + (contentAreaHeight - leftH) / 2.0);

                //noinspection SuspiciousNameCombination
                graphicPane.resizeRelocate(currentX, leftY, leftW, leftH);
                currentX = snapPositionX(currentX + leftW + snapSpaceX(sidebar.getCellSpacing()));
            } else {
                graphicPane.setVisible(false);
            }

            // accessory block
            double rightPadding = snapSpaceX(cellPadding.getRight());
            double accessoryW = computeAccessoryWidth();
            double accessoryH = snapSizeY(accessoryPane.prefHeight(-1));

            // minimum cell width at which accessory won't overlap the graphic
            double accessoryMinW = leftIndent
                + (leftW > 0 ? leftW + snapSpaceX(sidebar.getCellSpacing()) : 0)
                + accessoryW
                + rightPadding;

            boolean showAccessory = !collapsed && (w >= accessoryMinW);
            accessoryPane.setVisible(showAccessory);
            titleLabel.setVisible(!collapsed);

            if (!collapsed) {
                if (accessoryW > 0) {
                    double accessoryX = snapPositionX(w - rightPadding - accessoryW);
                    double accessoryY = snapPositionY(topPadding + (contentAreaHeight - accessoryH) / 2.0);
                    accessoryPane.resizeRelocate(accessoryX, accessoryY, accessoryW, accessoryH);
                }

                double labelH = snapSizeY(titleLabel.prefHeight(-1));
                double labelY = snapPositionY(topPadding + (contentAreaHeight - labelH) / 2.0);
                double maxLabelWidth = snapSizeX(
                    w
                        - rightPadding
                        - (accessoryW > 0 ? accessoryW + snapSpaceX(sidebar.getCellSpacing()) : 0)
                        - currentX
                );

                titleLabel.resizeRelocate(currentX, labelY, Math.max(0, maxLabelWidth), labelH);
            }
        }

        //*********************************************************************

        private void updatePseudoClasses() {
            pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, item.isSelected());
            pseudoClassStateChanged(SELECTED_WITHIN_PSEUDO_CLASS, item.isSelectedWithin());
        }

        private double computeUnconstrainedWidth() {
            double leftW = computeNodeWidth(item.getGraphic());
            double labelW = snapSizeX(titleLabel.prefWidth(-1));
            double accessoryW = computeAccessoryWidth();

            Insets cellPadding = sidebar.getCellPadding();
            double leftIndent = snapSpaceX(cellPadding.getLeft() + (level * sidebar.getIndent()));
            double spacing = snapSpaceX(sidebar.getCellSpacing());

            double totalW = leftIndent;
            if (leftW > 0) {
                totalW += leftW + spacing;
            }
            totalW += labelW;
            if (accessoryW > 0) {
                totalW += spacing + accessoryW;
            }
            totalW += snapSpaceX(cellPadding.getRight());

            return snapSizeX(totalW);
        }

        private double computeAccessoryWidth() {
            Node accessory = item.getAccessory();
            if (accessory != null) {
                return computeNodeWidth(accessory);
            }
            if (item instanceof SidebarGroup<T> group && group.isNotEmpty()) {
                ensureArrowPath();
                return computeNodeWidth(arrowPath);
            }
            return 0.0;
        }

        private void ensureArrowPath() {
            if (arrowPath == null) {
                arrowPath = new SVGPath();
                arrowPath.setContent("M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z");
                arrowPath.getStyleClass().add("arrow");
                arrowPath.setMouseTransparent(true);
            }
        }

        private void updateAccessory() {
            accessoryPane.getChildren().clear();

            Node accessory = item.getAccessory();
            if (accessory != null) {
                accessoryPane.getChildren().add(accessory);
            } else if (item instanceof SidebarGroup<T> group) {
                if (group.isNotEmpty() || !group.getItems().isEmpty()) {
                    ensureArrowPath();
                    if (group.isExpanded()) {
                        if (arrowPath != null) {
                            arrowPath.setRotate(180);
                        }
                    } else {
                        if (arrowPath != null) {
                            arrowPath.setRotate(0);
                        }
                    }
                    accessoryPane.getChildren().add(arrowPath);
                }
            }
        }

        private void updateGraphic() {
            graphicPane.getChildren().clear();
            Node graphic = item.getGraphic();
            if (graphic != null) {
                graphic.setMouseTransparent(true);
                graphicPane.getChildren().add(graphic);
                graphicPane.setVisible(true);
            } else {
                graphicPane.setVisible(false);
            }
        }
    }
    //endregion
}
