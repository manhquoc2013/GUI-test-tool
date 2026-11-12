/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

/**
 * A leaf navigation item within a {@link Sidebar}.
 *
 * <p>Represents selectable navigation destinations or action triggers. Can be added directly
 * to the sidebar, or grouped inside a {@link SidebarGroup}.
 *
 * @param <T> the type of value payload held by the navigation item
 * @see SidebarGroup
 * @see Sidebar
 */
public final class SidebarNav<T extends @Nullable Object> extends SidebarItem<T> {

    /**
     * Constructs a {@code SidebarNav} with text.
     *
     * @param text the display label
     */
    public SidebarNav(@Nullable String text) {
        this(text, null, null);
    }

    /**
     * Constructs a {@code SidebarNav} with text and graphic.
     *
     * @param text    the display label
     * @param graphic the graphic node
     */
    public SidebarNav(@Nullable String text,
                      @Nullable Node graphic) {
        this(text, graphic, null);
    }

    /**
     * Constructs a {@code SidebarNav} with text, graphic, and accessory node.
     *
     * @param text      the display label
     * @param graphic   the graphic node
     * @param accessory additional node rendered on the trailing side
     */
    public SidebarNav(@Nullable String text,
                      @Nullable Node graphic,
                      @Nullable Node accessory) {
        super(text, graphic, accessory);
    }
}
