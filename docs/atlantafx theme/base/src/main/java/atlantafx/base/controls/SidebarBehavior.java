/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import atlantafx.base.controls.SidebarSkin.SidebarItemCell;
import atlantafx.base.util.Disposable;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The default behavior for the {@link Sidebar} control.
 */
public class SidebarBehavior<T extends @Nullable Object> implements Disposable {

    private final Sidebar<@Nullable T> sidebar;
    private final EventHandler<KeyEvent> keyHandler;
    private final ChangeListener<Boolean> focusListener;

    private @Nullable SidebarItemCell<@Nullable T> focusedCell;

    public SidebarBehavior(Sidebar<@Nullable T> sidebar) {
        this.sidebar = sidebar;

        this.keyHandler = this::handleKeyPress;
        this.focusListener = (_, _, isFocused) -> {
            if (isFocused && focusedCell == null) {
                var cells = getSkin().getInteractiveCells();
                if (!cells.isEmpty()) {
                    setFocusedCell(cells.getFirst());
                }
            } else if (!isFocused && focusedCell != null) {
                focusedCell.setFocusedState(false);
            } else if (isFocused) {
                focusedCell.setFocusedState(true);
            }
        };

        this.sidebar.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        this.sidebar.focusedProperty().addListener(focusListener);
    }

    @SuppressWarnings("unchecked")
    private SidebarSkin<@Nullable T> getSkin() {
        return (SidebarSkin<T>) sidebar.getSkin();
    }

    public void handleItemClick(SidebarItem<@Nullable T> item,
                                SidebarItemCell<@Nullable T> cell) {
        // footer menu has a priority
        if (item == sidebar.getFooter() && sidebar.getFooterMenu() != null) {
            return;
        }

        setFocusedCell(cell);
        sidebar.requestFocus();

        if (item instanceof SidebarGroup<T> group) {
            if (group.isNotEmpty() && !sidebar.isCollapsed()) {
                group.setExpanded(!group.isExpanded());
            }
        } else if (item instanceof SidebarNav<T>) {
            sidebar.getSelectionModel().select(item);
        }

        sidebar.fireEvent(new SidebarEvent<>(SidebarEvent.ITEM_CLICK, item));
    }

    public void handleKeyPress(KeyEvent event) {
        var currentCell = focusedCell;
        var cells = getSkin().getInteractiveCells();

        if (cells.isEmpty()) {
            return;
        }

        if (currentCell == null || !cells.contains(currentCell)) {
            setFocusedCell(cells.getFirst());
            currentCell = cells.getFirst();
        }

        var item = currentCell.getItem();

        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            handleItemClick(item, currentCell);
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT) {
            if (item instanceof SidebarGroup<T> group && group.isNotEmpty()
                && !group.isExpanded() && !sidebar.isCollapsed()) {

                group.setExpanded(true);
                event.consume();
            }
        } else if (event.getCode() == KeyCode.LEFT) {
            if (item instanceof SidebarGroup<T> group && group.isNotEmpty()
                && group.isExpanded() && !sidebar.isCollapsed()) {

                group.setExpanded(false);
                event.consume();
            } else if (item.getParentGroup() != null) {
                // if we are inside a child element, moving left transfers focus to the parent group
                var parentCell = getSkin().findCellForItem(item.getParentGroup());
                if (parentCell != null) {
                    setFocusedCell(parentCell);
                }
                event.consume();
            }
        } else if (event.getCode() == KeyCode.DOWN) {
            var nextItem = getNextVisibleItem(item);
            if (nextItem != null) {
                var cell = getSkin().findCellForItem(nextItem);
                if (cell != null) {
                    setFocusedCell(cell);
                }
            }
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            var prevItem = getPreviousVisibleItem(item);
            if (prevItem != null) {
                var cell = getSkin().findCellForItem(prevItem);
                if (cell != null) {
                    setFocusedCell(cell);
                }
            }
            event.consume();
        }
    }

    @Override
    public void dispose() {
        sidebar.removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        sidebar.focusedProperty().removeListener(focusListener);
    }

    //*************************************************************************

    // VisibleForTesting
    @Nullable SidebarItemCell<@Nullable T> getFocusedCell() {
        return focusedCell;
    }

    // VisibleForTesting
    void setFocusedCell(@Nullable SidebarItemCell<@Nullable T> cell) {
        var cells = getSkin().getInteractiveCells();

        if (cell != null && !cells.contains(cell)) {
            cell = cells.isEmpty() ? null : cells.getFirst();
        }

        var oldCell = focusedCell;
        if (oldCell != null) {
            oldCell.setFocusedState(false);
        }

        focusedCell = cell;

        if (cell != null) {
            if (sidebar.isFocused()) {
                cell.setFocusedState(true);
            }
            getSkin().scrollTo(cell);
        }
    }

    private boolean isFocusable(SidebarItem<@Nullable T> item) {
        // caption should not receive focus when using arrow keys
        return !(item instanceof SidebarSection);
    }

    private @Nullable SidebarItem<@Nullable T> getNextVisibleItem(@Nullable SidebarItem<@Nullable T> current) {
        if (current == null) {
            return getFirstFocusableItem();
        }

        var candidate = getNextTreeNode(current);

        // traverse up/down the tree until an interactive element is found
        while (candidate != null && candidate != current) {
            if (isFocusable(candidate)) {
                return candidate;
            }
            candidate = getNextTreeNode(candidate);
        }

        // if we reached the end and found nothing (or returned to the starting point),
        // wrap around to the beginning
        var first = getFirstFocusableItem();
        return (first != null && !first.equals(current)) ? first : null;
    }

    private @Nullable SidebarItem<@Nullable T> getPreviousVisibleItem(@Nullable SidebarItem<@Nullable T> current) {
        if (current == null) {
            return getLastFocusableItem();
        }

        var candidate = getPreviousTreeNode(current);

        while (candidate != null && candidate != current) {
            if (isFocusable(candidate)) {
                return candidate;
            }
            candidate = getPreviousTreeNode(candidate);
        }

        // if we reached the very top — wrap around to the end of the list
        var last = getLastFocusableItem();
        return (last != null && !last.equals(current)) ? last : null;
    }

    private @Nullable SidebarItem<@Nullable T> getFirstFocusableItem() {
        for (var item : sidebar.getItems()) {
            if (isFocusable(item)) {
                return item;
            }

            if (item instanceof SidebarGroup<T> group && group.isExpanded() && !sidebar.isCollapsed()) {
                for (var child : group.getItems()) {
                    if (isFocusable(child)) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    private @Nullable SidebarItem<@Nullable T> getLastFocusableItem() {
        var items = sidebar.getItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            var lastLeaf = getLastVisibleDescendant(items.get(i));
            if (isFocusable(lastLeaf)) {
                return lastLeaf;
            }
        }
        return null;
    }

    private @Nullable SidebarItem<@Nullable T> getNextTreeNode(SidebarItem<@Nullable T> current) {
        // step inward if this is an expanded group with children
        if (current instanceof SidebarGroup<T> group && group.isExpanded()
            && !group.getItems().isEmpty() && !sidebar.isCollapsed()) {

            return group.getItems().getFirst();
        }

        // step sideways or upward
        SidebarItem<T> candidate = current;
        while (candidate != null) {
            var parent = candidate.getParentGroup();
            List<? extends SidebarItem<T>> siblings = (parent != null) ? parent.getItems() : sidebar.getItems();

            int idx = siblings.indexOf(candidate);
            if (idx >= 0 && idx < siblings.size() - 1) {
                return siblings.get(idx + 1);
            }
            candidate = parent; // move up to the parent and look for its next sibling
        }

        // if we reach the very end of the root elements - return null for wrapping
        return null;
    }

    private @Nullable SidebarItem<@Nullable T> getPreviousTreeNode(SidebarItem<@Nullable T> current) {
        var parent = current.getParentGroup();
        List<? extends SidebarItem<T>> siblings = (parent != null) ? parent.getItems() : sidebar.getItems();

        int idx = siblings.indexOf(current);
        if (idx > 0) {
            var prevSibling = siblings.get(idx - 1);
            return getLastVisibleDescendant(prevSibling);
        }

        // if we are the first element in children — return the parent group itself
        return parent;
    }

    private SidebarItem<@Nullable T> getLastVisibleDescendant(SidebarItem<@Nullable T> item) {
        if (item instanceof SidebarGroup<T> group && group.isExpanded()
            && !group.getItems().isEmpty() && !sidebar.isCollapsed()) {

            return getLastVisibleDescendant(group.getItems().getLast());
        }
        return item;
    }
}
