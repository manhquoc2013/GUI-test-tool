/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import atlantafx.base.shim.event.EventHandlerManager;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.*;
import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A base class for all elements contained within a {@link Sidebar}.
 *
 * @param <T> the type of value payload held by the sidebar item
 * @see SidebarNav
 * @see SidebarGroup
 * @see SidebarSection
 * @see SidebarHeader
 * @see SidebarFooter
 */
public abstract sealed class SidebarItem<T extends @Nullable Object>
    implements EventTarget permits SidebarFooter, SidebarGroup, SidebarHeader, SidebarNav, SidebarSection {

    /**
     * Constructs a {@code SidebarItem} with the specified text title.
     *
     * @param text the title text of the item, or {@code null}
     */
    public SidebarItem(@Nullable String text) {
        this(text, null, null);
    }

    /**
     * Constructs a {@code SidebarItem} with the specified text title and graphic.
     *
     * @param text    the title text of the item, or {@code null}
     * @param graphic the graphic node displayed alongside the text, or {@code null}
     */
    public SidebarItem(@Nullable String text,
                       @Nullable Node graphic) {
        this(text, graphic, null);
    }

    /**
     * Constructs a {@code SidebarItem} with text, graphic, and accessory node.
     *
     * @param text      the title text of the item, or {@code null}
     * @param graphic   the graphic node, or {@code null}
     * @param accessory additional node rendered on the trailing side, or {@code null}
     */
    public SidebarItem(@Nullable String text,
                       @Nullable Node graphic,
                       @Nullable Node accessory) {
        this.text.set(text);
        this.graphic.set(graphic);
        this.accessory.set(accessory);

        initPropertyListeners();
    }

    private void initPropertyListeners() {
        text.addListener((_, _, _) ->
            fireEvent(new SidebarItemEvent<>(SidebarItemEvent.textChanged(), this)));
        graphic.addListener((_, _, _) ->
            fireEvent(new SidebarItemEvent<>(SidebarItemEvent.graphicChanged(), this)));
        accessory.addListener((_, _, _) ->
            fireEvent(new SidebarItemEvent<>(SidebarItemEvent.graphicChanged(), this)));
    }

    // ~

    private final ObservableList<String> styleClass = FXCollections.observableArrayList("sidebar-cell");

    /**
     * Returns the list of CSS style classes associated with this item.
     *
     * @return the list of CSS style classes
     */
    public ObservableList<String> getStyleClass() {
        return styleClass;
    }

    // ~

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    /**
     * The title text property of the item.
     *
     * @return the text property
     */
    public StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the title text of this item.
     *
     * @return the title text, or {@code null}
     */
    public @Nullable String getText() {
        return text.get();
    }

    /**
     * Sets the title text of this item.
     *
     * @param value the title text to set
     */
    public void setText(@Nullable String value) {
        text.set(value);
    }

    // ~

    private final ObjectProperty<@Nullable T> value = new SimpleObjectProperty<>(this, "value");

    /**
     * The custom value object (payload) associated with this item.
     *
     * @return the value property
     * @see #getValue()
     * @see #setValue(Object)
     */
    public ObjectProperty<@Nullable T> valueProperty() {
        return value;
    }

    /**
     * Returns the payload value attached to this item.
     *
     * @return the item value, or {@code null}
     */
    public @Nullable T getValue() {
        return value.get();
    }

    /**
     * Sets a custom payload value for this item.
     *
     * @param value the value to associate with this item
     */
    public void setValue(@Nullable T value) {
        this.value.set(value);
    }

    // ~

    private final ObjectProperty<@Nullable Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * The left graphic node property.
     *
     * @return the graphic property
     * @see #getGraphic()
     * @see #setGraphic(Node)
     */
    public ObjectProperty<@Nullable Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the left graphic node of this item.
     *
     * @return the graphic node, or {@code null}
     */
    public @Nullable Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the left graphic node displayed with this item.
     *
     * @param graphic the graphic node to set
     */
    public void setGraphic(@Nullable Node graphic) {
        this.graphic.set(graphic);
    }

    // ~

    private final ObjectProperty<@Nullable Node> accessory = new SimpleObjectProperty<>(this, "accessory");

    /**
     * The trailing accessory node property (e.g., badges, counters, indicators, menus).
     *
     * @return the accessory property
     */
    public ObjectProperty<@Nullable Node> accessoryProperty() {
        return accessory;
    }

    /**
     * Returns the accessory node rendered at the trailing edge of this item.
     *
     * @return the accessory node, or {@code null}
     */
    public @Nullable Node getAccessory() {
        return accessory.get();
    }

    /**
     * Sets the accessory node rendered at the trailing edge of this item.
     *
     * @param accessory the accessory node to set
     */
    public void setAccessory(@Nullable Node accessory) {
        this.accessory.set(accessory);
    }

    // ~

    private final Map<String, @Nullable Object> properties = new HashMap<>();

    /**
     * Returns a map of custom properties stored in this item.
     * Useful for storing metadata or state external to the standard properties.
     *
     * @return a map for storing custom key-value metadata
     */
    public Map<String, @Nullable Object> getProperties() {
        return properties;
    }

    // ~

    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false);

    /**
     * Indicates whether this item is currently selected.
     *
     * @return property reflecting the selection state
     */
    BooleanProperty selectedProperty() {
        return selected;
    }

    /**
     * Returns whether this item is currently selected in the sidebar.
     *
     * @return {@code true} if selected, {@code false} otherwise
     */
    public boolean isSelected() {
        return selected.get();
    }

    void setSelected(boolean value) {
        selected.set(value);
    }

    // ~

    private final BooleanProperty selectedWithin = new SimpleBooleanProperty(this, "selectedWithin", false);

    /**
     * Indicates whether any child item within this item (e.g. inside a {@link SidebarGroup}) is selected.
     *
     * @return property reflecting nested child selection
     */
    BooleanProperty selectedWithinProperty() {
        return selectedWithin;
    }

    /**
     * Returns whether a descendant of this item is selected.
     *
     * @return {@code true} if a child is selected, {@code false} otherwise
     */
    public boolean isSelectedWithin() {
        return selectedWithin.get();
    }

    void setSelectedWithin(boolean value) {
        selectedWithin.set(value);
    }

    // ~

    private final ObjectProperty<@Nullable SidebarGroup<@Nullable T>> parentGroup = new SimpleObjectProperty<>(this, "parentGroup", null);

    /**
     * References to the parent group containing this item, if nested.
     *
     * @return parent group property
     */
    ObjectProperty<@Nullable SidebarGroup<T>> parentGroupProperty() {
        return parentGroup;
    }

    /**
     * Returns the parent {@link SidebarGroup} containing this item.
     *
     * @return the parent group, or {@code null} if this is a top-level item
     */
    public @Nullable SidebarGroup<@Nullable T> getParentGroup() {
        return parentGroup.get();
    }

    void setParentGroup(@Nullable SidebarGroup<@Nullable T> parent) {
        this.parentGroup.set(parent);
    }

    //region EVENT HANDLER
    //*************************************************************************

    private final EventHandlerManager eventHandlerManager = new EventHandlerManager(this);

    /**
     * Registers an event handler for events of the given type.
     *
     * @param <E>          the event type
     * @param eventType    the event type to handle
     * @param eventHandler the event handler callback
     */
    @Override
    public <E extends Event> void addEventHandler(
        EventType<E> eventType,
        EventHandler<? super E> eventHandler) {
        eventHandlerManager.addEventHandler(eventType, eventHandler);
    }

    /**
     * Unregisters an event handler for events of the given type.
     *
     * @param <E>          the event type
     * @param eventType    the event type to unregister
     * @param eventHandler the event handler callback
     */
    @Override
    public <E extends Event> void removeEventHandler(
        EventType<E> eventType,
        EventHandler<? super E> eventHandler) {
        eventHandlerManager.removeEventHandler(eventType, eventHandler);
    }

    @Override
    public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
        return tail.prepend(new SidebarItemDispatcher(this.eventHandlerManager));
    }

    /**
     * Fires the given event through this item's event dispatch chain.
     *
     * @param event the event to fire
     */
    public void fireEvent(Event event) {
        Event.fireEvent(this, event);
    }

    /**
     * Custom event dispatcher implementation for handling event bubbling on {@link SidebarItem}.
     */
    public static class SidebarItemDispatcher implements EventDispatcher {

        private final EventHandlerManager eventHandlerManager;

        public SidebarItemDispatcher(EventHandlerManager eventHandlerManager) {
            this.eventHandlerManager = eventHandlerManager;
        }

        @Override
        public @Nullable Event dispatchEvent(Event event, EventDispatchChain tail) {
            event = tail.dispatchEvent(event);

            if (event != null && !event.isConsumed()) {
                event = eventHandlerManager.dispatchBubblingEvent(event);
            }

            return event;
        }
    }
    //endregion
}
