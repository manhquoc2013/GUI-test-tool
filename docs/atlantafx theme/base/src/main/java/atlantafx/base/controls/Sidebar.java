/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Skin;
import javafx.util.Duration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A side navigation component that supports collapsing, item selection, header, footer,
 * and transition animations.
 *
 * <h6>Usage Examples</h6>
 *
 * <p>Basic setup:
 *
 * <pre>{@code
 * var sidebar = new Sidebar<String>();
 * sidebar.setPrefWidth(250);
 *
 * sidebar.setHeader(new SidebarHeader<>("Acme Corp", new FontIcon(Feather.BOX)));
 * sidebar.setFooter(new SidebarFooter<>("Alex Developer", new FontIcon(Feather.USER)));
 *
 * // create menu items
 * var mainSection = new SidebarSection<String>("Main");
 * var dashboardItem = new SidebarNav<String>("Dashboard", new FontIcon(Feather.GRID));
 * dashboardItem.setValue("DASHBOARD"); // set item's payload
 *
 * var analyticsGroup = new SidebarGroup<String>("Analytics", new FontIcon(Feather.BAR_CHART_2));
 * var realtimeItem = new SidebarNav<String>("Realtime", new FontIcon(Feather.ACTIVITY));
 * var reportsItem = new SidebarNav<String>("Reports", new FontIcon(Feather.FILE_TEXT));
 * analyticsGroup.getItems().addAll(realtimeItem, reportsItem);
 *
 * sidebar.getItems().addAll(mainSection, dashboardItem, analyticsGroup);
 * }</pre>
 *
 * <p>Handling events & selection:
 *
 * <pre>{@code
 * // listen to item click events (including header, footer, and items)
 * sidebar.addEventHandler(SidebarEvent.ITEM_CLICK, event -> {
 *     var item = event.getItem();
 *     System.out.println("Clicked: " + item.getText());
 * });
 *
 * // track selection model changes
 * sidebar.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
 *     if (newVal != null) {
 *         System.out.println("Selected value: " + newVal.getValue());
 *     }
 * });
 * }</pre>
 *
 * <p>Dynamic content updates:
 *
 * <pre>{@code
 * var statusItem = new SidebarNav<String>("Status", new FontIcon(Feather.INFO));
 *
 * // modify item text or graphic dynamically
 * statusItem.setText("Status: Active");
 * statusItem.setAccessory(new Label("OK"));
 *
 * // notify sidebar UI to reflect changes
 * statusItem.fireEvent(new SidebarItemEvent<>(SidebarItemEvent.itemChanged(), statusItem));
 * }</pre>
 *
 * <p>Programmatic selection & scrolling:
 *
 * <pre>{@code
 * var targetItem = ...; // any leaf item or group
 *
 * sidebar.getSelectionModel().select(targetItem);
 * sidebar.scrollTo(targetItem); // expands parent groups if necessary and scrolls into view
 * }</pre>
 *
 * <h5>CSS Properties</h5>
 * <p>
 * The {@code Sidebar} control supports the following CSS properties:
 * </p>
 * <table border="1">
 *   <tr>
 *     <th>Property</th>
 *     <th>Type</th>
 *     <th>Default Value</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>{@code -fx-cell-height}</td>
 *     <td>{@link Number}</td>
 *     <td>24</td>
 *     <td>Specifies the inner content height of item cells.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code -fx-cell-padding}</td>
 *     <td>{@link Insets}</td>
 *     <td>10 15 10 15</td>
 *     <td>Specifies the padding applied around content inside item cells.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code -fx-cell-spacing}</td>
 *     <td>{@link Number}</td>
 *     <td>10</td>
 *     <td>Specifies horizontal spacing between cell elements (graphic, title, and accessory).</td>
 *   </tr>
 *   <tr>
 *     <td>{@code -fx-indent}</td>
 *     <td>{@link Number}</td>
 *     <td>15</td>
 *     <td>Specifies horizontal indentation offset applied for nested items.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code -fx-collapsed-content-width}</td>
 *     <td>{@link Number}</td>
 *     <td>24</td>
 *     <td>Specifies the width allocated for content when the sidebar is collapsed.<br/>
 *     In the collapsed state, the {@code Sidebar} displays icons only. The total {@code Sidebar}<br/>
 *     width in this state is equal to {@code -fx-collapsed-content-width}<br/>
 *     plus the left and right {@code -fx-cell-padding}, which implies that all icons<br/>
 *     should have uniform dimensions.</td>
 *   </tr>
 * </table>
 *
 * @param <T> the type of the value payload attached to items in this sidebar
 */
public class Sidebar<T extends @Nullable Object> extends Control {

    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200);
    private static final PseudoClass COLLAPSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("collapsed");

    /**
     * Creates a new {@code Sidebar} control with default settings and selection model.
     */
    public Sidebar() {
        getStyleClass().add("sidebar");
        setSelectionModel(new SidebarSelectionModel<>(this));
        setFocusTraversable(true);

        items.addListener((ListChangeListener<SidebarItem<T>>) c -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (var item : c.getRemoved()) {
                        item.setParentGroup(null);
                    }
                }
                if (c.wasAdded()) {
                    for (var item : c.getAddedSubList()) {
                        item.setParentGroup(null);
                    }
                }
            }
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SidebarSkin<>(this);
    }

    // ~

    private final BooleanProperty collapsed = new SimpleBooleanProperty(this, "collapsed", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(COLLAPSED_PSEUDO_CLASS, get());
        }
    };

    /**
     * Indicates whether the sidebar is currently collapsed.
     *
     * @return the property holding the collapsed state
     */
    public BooleanProperty collapsedProperty() {
        return collapsed;
    }

    /**
     * Gets the value of the {@link #collapsedProperty()}.
     *
     * @return {@code true} if the sidebar is collapsed; {@code false} otherwise
     */
    public boolean isCollapsed() {
        return collapsed.get();
    }

    /**
     * Sets the value of the {@link #collapsedProperty()}.
     *
     * @param value {@code true} to collapse the sidebar; {@code false} to expand it
     */
    public void setCollapsed(boolean value) {
        collapsed.set(value);
    }

    // ~

    private final ObjectProperty<@Nullable SidebarHeader<@Nullable T>> header = new SimpleObjectProperty<>(this, "header");

    /**
     * Contains the optional header component positioned at the top of the sidebar.
     *
     * @return the property for the sidebar header
     */
    public ObjectProperty<@Nullable SidebarHeader<@Nullable T>> headerProperty() {
        return header;
    }

    /**
     * Returns the value of the {@link #headerProperty()}.
     *
     * @return the current header, or {@code null} if no header is displayed
     */
    public @Nullable SidebarHeader<@Nullable T> getHeader() {
        return header.get();
    }

    /**
     * Sets the value of the {@link #headerProperty()}.
     *
     * @param header the header component to set, or {@code null} to remove it
     */
    public void setHeader(@Nullable SidebarHeader<@Nullable T> header) {
        this.header.set(header);
    }

    // ~

    private final ObjectProperty<@Nullable SidebarFooter<@Nullable T>> footer = new SimpleObjectProperty<>(this, "footer");

    /**
     * Contains the optional footer component positioned at the bottom of the sidebar.
     *
     * @return the property for the sidebar footer
     */
    public ObjectProperty<@Nullable SidebarFooter<@Nullable T>> footerProperty() {
        return footer;
    }

    /**
     * Returns the value of the {@link #footerProperty()}.
     *
     * @return the current footer, or {@code null} if no footer is displayed
     */
    public @Nullable SidebarFooter<@Nullable T> getFooter() {
        return footer.get();
    }

    /**
     * Sets the value of the {@link #footerProperty()}.
     *
     * @param footer the footer component to set, or {@code null} to remove it
     */
    public void setFooter(@Nullable SidebarFooter<@Nullable T> footer) {
        this.footer.set(footer);
    }

    // ~

    private final ObservableList<SidebarItem<@Nullable T>> items = FXCollections.observableArrayList();

    /**
     * Returns the observable list of items displayed within this sidebar.
     *
     * @return the observable list containing the items
     */
    public ObservableList<SidebarItem<@Nullable T>> getItems() {
        return items;
    }

    // ~

    private final ObjectProperty<SingleSelectionModel<SidebarItem<@Nullable T>>> selectionModel = new SimpleObjectProperty<>(this, "selectionModel");

    /**
     * Contains the model used to track and manage item selection within the sidebar.
     *
     * @return the property for the selection model
     */
    public ObjectProperty<SingleSelectionModel<SidebarItem<@Nullable T>>> selectionModelProperty() {
        return selectionModel;
    }

    /**
     * Returns the value of the {@link #selectionModelProperty()}.
     *
     * @return the active selection model
     */
    public SingleSelectionModel<SidebarItem<@Nullable T>> getSelectionModel() {
        return selectionModel.get();
    }

    /**
     * Sets the value of the {@link #selectionModelProperty()}.
     *
     * @param model the selection model to assign to this sidebar
     */
    public void setSelectionModel(SingleSelectionModel<SidebarItem<@Nullable T>> model) {
        this.selectionModel.set(model);
    }

    // ~

    private final ObjectProperty<Duration> animationDuration = new SimpleObjectProperty<>(this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Contains the duration of transition animations within the sidebar.
     *
     * @return the property for the animation duration
     */
    public ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the value of the {@link #animationDurationProperty()}.
     *
     * @return the current animation duration
     */
    public Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the value of the {@link #animationDurationProperty()}.
     *
     * <p>If the specified duration is {@code null}, it defaults to 200 milliseconds.
     *
     * @param duration the duration to apply, or {@code null} to reset to default
     */
    public void setAnimationDuration(@Nullable Duration duration) {
        this.animationDuration.set(Objects.requireNonNullElse(duration, DEFAULT_ANIMATION_DURATION));
    }

    // ~

    private final ObjectProperty<EventHandler<SidebarEvent<T>>> onItemClick = new SimpleObjectProperty<>(this, "onItemClick") {
        @Override
        protected void invalidated() {
            setEventHandler(SidebarEvent.itemClick(), get());
        }
    };

    /**
     * Contains the event handler triggered when an item in the sidebar is clicked.
     *
     * @return the property for the item click event handler
     */
    public final ObjectProperty<EventHandler<SidebarEvent<@Nullable T>>> onItemClickProperty() {
        return onItemClick;
    }

    /**
     * Returns the value of the {@link #onItemClickProperty()}.
     *
     * @return the current item click event handler, or {@code null} if none is set
     */
    public final EventHandler<SidebarEvent<@Nullable T>> getOnItemClick() {
        return onItemClick.get();
    }

    /**
     * Sets the value of the {@link #onItemClickProperty()}.
     *
     * @param value the event handler to set when an item is clicked
     */
    public final void setOnItemClick(EventHandler<SidebarEvent<@Nullable T>> value) {
        onItemClick.set(value);
    }

    // ~

    private final ObjectProperty<@Nullable ContextMenu> footerMenu = new SimpleObjectProperty<>(this, "footerMenu");

    /**
     * Contains the context menu associated with the footer area of the sidebar.
     *
     * @return the property for the footer context menu
     */
    public ObjectProperty<@Nullable ContextMenu> footerMenuProperty() {
        return footerMenu;
    }

    /**
     * Gets the value of the {@link #footerMenuProperty()}.
     *
     * @return the current footer context menu, or {@code null} if none is set
     */
    public @Nullable ContextMenu getFooterMenu() {
        return footerMenu.get();
    }

    /**
     * Sets the value of the {@link #footerMenuProperty()}.
     *
     * @param menu the context menu to attach to the footer, or {@code null} to clear it
     */
    public void setFooterMenu(@Nullable ContextMenu menu) {
        this.footerMenu.set(menu);
    }

    /**
     * Scrolls the view to reveal the specified item, expanding any parent groups if necessary.
     * Does nothing if the specified item is {@code null}.
     *
     * @param item the item to scroll into view
     */
    @SuppressWarnings("unchecked")
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

        if (getSkin() instanceof SidebarSkin<?> skin) {
            ((SidebarSkin<T>) skin).scrollTo(item);
        }
    }

    // skin accessors
    double getCellHeight() {
        return cellHeight.get();
    }

    Insets getCellPadding() {
        return cellPadding.get();
    }

    double getCellSpacing() {
        return cellSpacing.get();
    }

    double getIndent() {
        return indent.get();
    }

    double getCollapsedWidth() {
        var padding = getCellPadding();
        return padding.getLeft() + collapsedContentWidth.get() + padding.getRight();
    }

    //region STYLEABLE
    //*************************************************************************

    private final StyleableDoubleProperty cellHeight = new SimpleStyleableDoubleProperty(
        StyleableProperties.CELL_HEIGHT, this, "cellHeight", 24.0);

    private final StyleableObjectProperty<Insets> cellPadding = new SimpleStyleableObjectProperty<>(
        StyleableProperties.CELL_PADDING, this, "cellPadding", new Insets(10, 15, 10, 15));

    private final StyleableDoubleProperty cellSpacing = new SimpleStyleableDoubleProperty(
        StyleableProperties.CELL_SPACING, this, "cellSpacing", 10.0);

    private final StyleableDoubleProperty indent = new SimpleStyleableDoubleProperty(
        StyleableProperties.INDENT, this, "indent", 15.0);

    private final StyleableDoubleProperty collapsedContentWidth = new SimpleStyleableDoubleProperty(
        StyleableProperties.COLLAPSED_CONTENT_WIDTH, this, "collapsedContentWidth", 24.0);

    private static class StyleableProperties {

        private static final CssMetaData<Sidebar<?>, Number> CELL_HEIGHT =
            new CssMetaData<>("-fx-cell-height", SizeConverter.getInstance(), 24.0) {
                @Override
                public boolean isSettable(Sidebar<?> sidebar) {
                    return !sidebar.cellHeight.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(Sidebar<?> sidebar) {
                    return sidebar.cellHeight;
                }
            };

        private static final CssMetaData<Sidebar<?>, Insets> CELL_PADDING =
            new CssMetaData<>("-fx-cell-padding", InsetsConverter.getInstance(), new Insets(10, 15, 10, 15)) {
                @Override
                public boolean isSettable(Sidebar<?> sidebar) {
                    return !sidebar.cellPadding.isBound();
                }

                @Override
                public StyleableProperty<Insets> getStyleableProperty(Sidebar<?> n) {
                    return n.cellPadding;
                }
            };

        private static final CssMetaData<Sidebar<?>, Number> CELL_SPACING =
            new CssMetaData<>("-fx-cell-spacing", SizeConverter.getInstance(), 10.0) {
                @Override
                public boolean isSettable(Sidebar<?> sidebar) {
                    return !sidebar.cellSpacing.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(Sidebar<?> sidebar) {
                    return sidebar.cellSpacing;
                }
            };

        private static final CssMetaData<Sidebar<?>, Number> INDENT =
            new CssMetaData<>("-fx-indent", SizeConverter.getInstance(), 15.0) {
                @Override
                public boolean isSettable(Sidebar<?> sidebar) {
                    return !sidebar.indent.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(Sidebar<?> n) {
                    return n.indent;
                }
            };

        private static final CssMetaData<Sidebar<?>, Number> COLLAPSED_CONTENT_WIDTH =
            new CssMetaData<>("-fx-collapsed-content-width", SizeConverter.getInstance(), 24.0) {
                @Override
                public boolean isSettable(Sidebar<?> sidebar) {
                    return !sidebar.collapsedContentWidth.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(Sidebar<?> sidebar) {
                    return sidebar.collapsedContentWidth;
                }
            };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            var list = new ArrayList<>(Control.getClassCssMetaData());
            list.add(CELL_HEIGHT);
            list.add(CELL_PADDING);
            list.add(CELL_SPACING);
            list.add(INDENT);
            list.add(COLLAPSED_CONTENT_WIDTH);
            STYLEABLES = Collections.unmodifiableList(list);
        }
    }

    /**
     * Returns the CSS metadata associated with the {@code Sidebar} class.
     *
     * @return an unmodifiable list of CSS metadata entries
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
    //endregion
}