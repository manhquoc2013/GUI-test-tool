/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

/**
 * A specialized item pinned at the top header region of a {@link Sidebar}.
 *
 * <p>Typically used for branding, logo display, or application title headers.
 *
 * @param <T> the type of value payload
 * @see Sidebar#headerProperty()
 */
public final class SidebarHeader<T extends @Nullable Object> extends SidebarItem<T> {

    /**
     * Constructs a {@code SidebarHeader} with text and graphic.
     *
     * @param text    the title text
     * @param graphic the graphic node
     */
    public SidebarHeader(@Nullable String text,
                         @Nullable Node graphic) {
        super(text, graphic, null);
    }

    /**
     * Constructs a {@code SidebarHeader} with text, graphic, and accessory node.
     *
     * @param text      the title text
     * @param graphic   the graphic node
     * @param accessory additional node rendered on the trailing side
     */
    public SidebarHeader(@Nullable String text,
                         @Nullable Node graphic,
                         @Nullable Node accessory) {
        super(text, graphic, accessory);
    }
}