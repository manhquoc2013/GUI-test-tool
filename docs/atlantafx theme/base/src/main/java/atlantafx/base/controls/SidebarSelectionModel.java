/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.scene.AccessibleAttribute;
import javafx.scene.control.SingleSelectionModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The default selection model for the {@link Sidebar} control.
 *
 * <p>This model doesn't allow {@link SidebarGroup} items to be selected.
 */
public class SidebarSelectionModel<T extends @Nullable Object>
    extends SingleSelectionModel<@Nullable SidebarItem<T>> {

    private final Sidebar<@Nullable T> sidebar;

    public SidebarSelectionModel(Sidebar<@Nullable T> sidebar) {
        this.sidebar = Objects.requireNonNull(sidebar, "Sidebar cannot be null");
    }

    @Override
    public void select(int index) {
        if (index < 0) {
            clearSelection();
            return;
        }

        var items = collectSelectableItems();
        if (index >= items.size()) {
            return;
        }

        select(items.get(index));
    }

    @Override
    public void select(@Nullable SidebarItem<@Nullable T> item) {
        if (item == null) {
            clearSelection();
            return;
        }

        var current = getSelectedItem();
        if (current != null && current.equals(item) && current.isSelected()) {
            return;
        }

        if (current != null) {
            current.setSelected(false);
        }

        expandItemGroup(item);

        setSelectedItem(item);
        item.setSelected(true);

        // update the index as the branch is now guaranteed to be expanded
        var items = collectSelectableItems();
        int idx = items.indexOf(item);
        setSelectedIndex(idx);

        sidebar.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
    }

    @Override
    public void clearSelection() {
        var current = getSelectedItem();
        if (current != null) {
            current.setSelected(false);
        }
        super.clearSelection();
    }

    @Override
    protected @Nullable SidebarItem<@Nullable T> getModelItem(int index) {
        var items = collectSelectableItems();
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    @Override
    protected int getItemCount() {
        return collectSelectableItems().size();
    }

    //*************************************************************************

    private void expandItemGroup(SidebarItem<T> item) {
        var group = item.getParentGroup();
        while (group != null) {
            if (!group.isExpanded()) {
                group.setExpanded(true);
            }
            group = group.getParentGroup();
        }
    }

    private List<SidebarItem<@Nullable T>> collectSelectableItems() {
        List<SidebarItem<T>> result = new ArrayList<>();
        for (var item : sidebar.getItems()) {
            collectSelectableItems(item, result);
        }
        return result;
    }

    private void collectSelectableItems(SidebarItem<@Nullable T> item, List<SidebarItem<@Nullable T>> list) {
        if (item instanceof SidebarNav<T> leaf) {
            list.add(leaf);
        } else if (item instanceof SidebarGroup<T> group) {
            for (var child : group.getItems()) {
                collectSelectableItems(child, list);
            }
        }
    }
}
