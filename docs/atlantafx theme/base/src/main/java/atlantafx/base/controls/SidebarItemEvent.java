/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.event.Event;
import javafx.event.EventType;
import org.jspecify.annotations.Nullable;

/**
 * An {@link Event} that represents state changes occurring within a {@link SidebarItem},
 * such as modifications to its text, graphic, or accessory node.
 *
 * <p>These events are fired by individual {@link SidebarItem} instances to notify skin
 * implementations or application listeners that the item's visual properties have changed
 * and require a layout or render update.
 *
 * @param <T> the type of value payload held by the associated sidebar item
 * @see SidebarItem
 */
public class SidebarItemEvent<T extends @Nullable Object> extends Event {

    /**
     * Common supertype for all {@code SidebarItemEvent} types.
     */
    public static final EventType<SidebarItemEvent<?>> ANY = new EventType<>(
        Event.ANY, "SIDEBAR_ITEM_ANY"
    );

    /**
     * Event type triggered when any visual or structural property of a {@link SidebarItem} changes.
     * Serves as the parent event type for specific property change events like {@link #TEXT_CHANGED}
     * and {@link #GRAPHIC_CHANGED}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EventType<SidebarItemEvent<?>> ITEM_CHANGED = new EventType<>(
        (EventType) ANY, "SIDEBAR_ITEM_CHANGED"
    );

    /**
     * Event type triggered when the text property of a {@link SidebarItem} is modified.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EventType<SidebarItemEvent<?>> TEXT_CHANGED = new EventType<>(
        (EventType) ITEM_CHANGED, "SIDEBAR_ITEM_TEXT_CHANGED"
    );

    /**
     * Event type triggered when either the graphic or accessory node of a {@link SidebarItem}
     * is modified.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EventType<SidebarItemEvent<?>> GRAPHIC_CHANGED = new EventType<>(
        (EventType) ITEM_CHANGED, "SIDEBAR_ITEM_GRAPHIC_CHANGED"
    );

    private final SidebarItem<T> item;

    /**
     * Constructs a new {@code SidebarItemEvent} with the specified event type and source item.
     *
     * @param eventType the specific event type
     * @param item      the sidebar item associated with this event
     */
    public SidebarItemEvent(EventType<? extends SidebarItemEvent<?>> eventType,
                            SidebarItem<T> item) {
        super(eventType);
        this.item = item;
    }

    /**
     * Returns the {@link SidebarItem} associated with this event.
     *
     * @return the item on which the event occurred
     */
    public SidebarItem<T> getItem() {
        return item;
    }

    /**
     * Helper method to cast {@link #ITEM_CHANGED} to a type-safe {@link EventType} instance.
     *
     * @param <T> the item payload type
     * @return the type-safe event type for general item changes
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> EventType<SidebarItemEvent<T>> itemChanged() {
        return (EventType) ITEM_CHANGED;
    }

    /**
     * Helper method to cast {@link #TEXT_CHANGED} to a type-safe {@link EventType} instance.
     *
     * @param <T> the item payload type
     * @return the type-safe event type for text changes
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> EventType<SidebarItemEvent<T>> textChanged() {
        return (EventType) TEXT_CHANGED;
    }

    /**
     * Helper method to cast {@link #GRAPHIC_CHANGED} to a type-safe {@link EventType} instance.
     *
     * @param <T> the item payload type
     * @return the type-safe event type for graphic or accessory changes
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> EventType<SidebarItemEvent<T>> graphicChanged() {
        return (EventType) GRAPHIC_CHANGED;
    }
}