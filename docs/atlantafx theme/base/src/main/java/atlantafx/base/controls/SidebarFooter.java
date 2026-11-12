/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

/**
 * A specialized item pinned at the bottom footer region of a {@link Sidebar}.
 *
 * @param <T> the type of value payload
 * @see Sidebar#footerProperty()
 */
public final class SidebarFooter<T extends @Nullable Object> extends SidebarItem<T> {

    /**
     * Constructs a {@code SidebarFooter} with text and graphic.
     *
     * @param text    the display text
     * @param graphic the graphic node
     */
    public SidebarFooter(@Nullable String text,
                         @Nullable Node graphic) {
        super(text, graphic, null);
    }

    /**
     * Constructs a {@code SidebarFooter} with text, graphic, and accessory node.
     *
     * @param text      the display text
     * @param graphic   the graphic node
     * @param accessory additional node rendered on the trailing side
     */
    public SidebarFooter(@Nullable String text,
                         @Nullable Node graphic,
                         @Nullable Node accessory) {
        super(text, graphic, accessory);
    }
}