/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

/**
 * A non-selectable visual category header used to group related items within a {@link Sidebar}.
 *
 * <p>Displays category titles or dividers and does not trigger selection state.
 *
 * @param <T> the type of value payload
 */
public final class SidebarSection<T extends @Nullable Object> extends SidebarItem<T> {

    /**
     * Constructs a {@code SidebarSection} with text label.
     *
     * @param text the category header title
     */
    public SidebarSection(@Nullable String text) {
        super(text);
    }

    /**
     * Constructs a {@code SidebarSection} with text label and accessory node.
     *
     * @param text      the category header title
     * @param accessory additional node rendered on the trailing side
     */
    public SidebarSection(@Nullable String text,
                          @Nullable Node accessory) {
        super(text, null, accessory);
    }
}