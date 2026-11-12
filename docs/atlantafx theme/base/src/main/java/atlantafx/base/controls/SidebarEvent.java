/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.event.Event;
import javafx.event.EventType;
import org.jspecify.annotations.Nullable;

/**
 * An {@link Event} representing user interactions with elements inside a {@link Sidebar},
 * such as clicking navigation items, headers, or footers.
 *
 * @param <T> the type of value payload held by items in the sidebar
 * @see Sidebar
 * @see SidebarItem
 */
public class SidebarEvent<T extends @Nullable Object> extends Event {

    /**
     * Common supertype for all {@code SidebarEvent} types.
     */
    public static final EventType<SidebarEvent<?>> ANY = new EventType<>(
        Event.ANY, "SIDEBAR_ANY"
    );

    /**
     * Event type delivered when a user clicks on an item within the sidebar.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EventType<SidebarEvent<?>> ITEM_CLICK = new EventType<>(
        (EventType) ANY, "SIDEBAR_ITEM_CLICK"
    );

    private final SidebarItem<T> item;

    /**
     * Constructs a new {@code SidebarEvent} with the specified event type and clicked item.
     *
     * @param eventType the event type
     * @param item      the sidebar item associated with this event
     */
    public SidebarEvent(EventType<? extends SidebarEvent<?>> eventType,
                        SidebarItem<T> item) {
        super(eventType);
        this.item = item;
    }

    /**
     * Returns the {@link SidebarItem} that was targeted by this event.
     *
     * @return the clicked sidebar item
     */
    public SidebarItem<T> getItem() {
        return item;
    }

    /**
     * Helper method to cast {@link #ITEM_CLICK} to a type-safe {@link EventType} instance.
     *
     * @param <T> the item payload type
     * @return the type-safe event type for item clicks
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> EventType<SidebarEvent<T>> itemClick() {
        return (EventType) ITEM_CLICK;
    }
}