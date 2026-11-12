/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import atlantafx.base.util.Disposable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

/**
 * A collapsible container item within a {@link Sidebar} that groups multiple {@link SidebarNav}
 * sub-items.
 *
 * @param <T> the type of value payload held by items in the group
 * @see SidebarNav
 */
public final class SidebarGroup<T extends @Nullable Object>
    extends SidebarItem<T> implements Disposable {

    private final ObservableList<SidebarNav<@Nullable T>> items = FXCollections.observableArrayList();
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);
    private final ChangeListener<Boolean> childListener = (_, _, _) -> updateSelectedWithinState();

    /**
     * Constructs a {@code SidebarGroup} with text.
     *
     * @param text the display label for the group
     */
    public SidebarGroup(@Nullable String text) {
        this(text, null, null);
    }

    /**
     * Constructs a {@code SidebarGroup} with text and graphic.
     *
     * @param text    the display label for the group
     * @param graphic the graphic node for the group header
     */
    public SidebarGroup(@Nullable String text,
                        @Nullable Node graphic) {
        this(text, graphic, null);
    }

    /**
     * Constructs a {@code SidebarGroup} with text, graphic, and accessory node.
     *
     * @param text      the display label for the group
     * @param graphic   the graphic node for the group header
     * @param accessory additional node rendered on the trailing side
     */
    public SidebarGroup(@Nullable String text,
                        @Nullable Node graphic,
                        @Nullable Node accessory) {
        super(text, graphic, accessory);

        items.addListener((ListChangeListener<SidebarNav<T>>) c -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (var item : c.getRemoved()) {
                        item.setParentGroup(null);
                        item.selectedProperty().removeListener(childListener);
                        item.selectedWithinProperty().removeListener(childListener);
                    }
                }
                if (c.wasAdded()) {
                    for (var item : c.getAddedSubList()) {
                        item.setParentGroup(this);
                        item.selectedProperty().removeListener(childListener);
                        item.selectedWithinProperty().removeListener(childListener);

                        item.selectedProperty().addListener(childListener);
                        item.selectedWithinProperty().addListener(childListener);
                    }
                }
            }

            updateSelectedWithinState();
        });
    }

    /**
     * Returns the observable list of child items contained within this group.
     *
     * @return the list of child items
     */
    public ObservableList<SidebarNav<T>> getItems() {
        return items;
    }

    /**
     * Indicates whether the group's child items are currently visible.
     *
     * @return the expanded property
     */
    public BooleanProperty expandedProperty() {
        return expanded;
    }

    /**
     * Returns whether this group is expanded.
     *
     * @return {@code true} if expanded, {@code false} if collapsed
     */
    public boolean isExpanded() {
        return expanded.get();
    }

    /**
     * Sets whether this group should be expanded or collapsed.
     *
     * @param value {@code true} to expand, {@code false} to collapse
     */
    public void setExpanded(boolean value) {
        expanded.set(value);
    }

    /**
     * Checks if this group contains at least one child item.
     *
     * @return {@code true} if items list is non-empty, {@code false} otherwise
     */
    public boolean isNotEmpty() {
        return !items.isEmpty();
    }

    @Override
    public void dispose() {
        for (var item : items) {
            detachChildListeners(item);
        }
    }

    //*************************************************************************

    private void updateSelectedWithinState() {
        var hasSelectedChild = false;
        for (var child : items) {
            if (child.isSelected() || child.isSelectedWithin()) {
                hasSelectedChild = true;
                break;
            }
        }

        setSelectedWithin(hasSelectedChild);
    }

    private void detachChildListeners(SidebarNav<T> item) {
        item.selectedProperty().removeListener(childListener);
        item.selectedWithinProperty().removeListener(childListener);
    }
}
