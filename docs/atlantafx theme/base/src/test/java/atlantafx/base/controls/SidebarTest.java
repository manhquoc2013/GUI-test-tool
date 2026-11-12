/* SPDX-License-Identifier: MIT */

package atlantafx.base.controls;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
@SuppressWarnings("unchecked")
public class SidebarTest {

    private Sidebar<String> sidebar;

    @BeforeAll
    static void init() throws InterruptedException {
        var latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @BeforeEach
    void setUp() {
        sidebar = new Sidebar<>();
    }

    @Nested
    class SelectionModelTests {

        @Test
        @DisplayName("should select leaf item and expand parents")
        void testSelectLeafItemAndExpandParents() {
            var group = new SidebarGroup<String>("Group 1");
            var leaf1 = new SidebarNav<String>("Leaf 1");
            var leaf2 = new SidebarNav<String>("Leaf 2");
            group.getItems().addAll(leaf1, leaf2);

            sidebar.getItems().add(group);
            group.setExpanded(false);

            assertFalse(group.isExpanded());

            sidebar.getSelectionModel().select(leaf2);

            assertTrue(group.isExpanded());
            assertEquals(leaf2, sidebar.getSelectionModel().getSelectedItem());
            assertTrue(leaf2.isSelected());
            assertTrue(group.isSelectedWithin());
        }

        @Test
        @DisplayName("should reset selection flags when selection cleared")
        void testClearSelectionResetsFlags() {
            var group = new SidebarGroup<String>("Group 1");
            var leaf = new SidebarNav<String>("Leaf 1");
            group.getItems().add(leaf);
            sidebar.getItems().add(group);

            sidebar.getSelectionModel().select(leaf);
            assertTrue(leaf.isSelected());
            assertTrue(group.isSelectedWithin());

            sidebar.getSelectionModel().clearSelection();

            assertFalse(leaf.isSelected());
            assertFalse(group.isSelectedWithin());
        }

        @Test
        @DisplayName("should map indices to selectable items correctly")
        void testSelectByIndex() {
            var header = new SidebarSection<String>("Header");
            var leaf1 = new SidebarNav<String>("Leaf 1");
            var group = new SidebarGroup<String>("Group");
            var leaf2 = new SidebarNav<String>("Leaf 2");
            group.getItems().add(leaf2);

            sidebar.getItems().addAll(header, leaf1, group);

            sidebar.getSelectionModel().select(0);
            assertEquals(leaf1, sidebar.getSelectionModel().getSelectedItem());

            sidebar.getSelectionModel().select(1);
            assertEquals(leaf2, sidebar.getSelectionModel().getSelectedItem());
        }
    }

    @Nested
    class DynamicElementsTests {

        @Test
        @DisplayName("should update relationships on dynamic addition")
        void testDynamicSubItemAddition() {
            var group = new SidebarGroup<String>("Group");
            sidebar.getItems().add(group);

            var dynamicLeaf = new SidebarNav<String>("Dynamic");
            assertNull(dynamicLeaf.getParentGroup());

            group.getItems().add(dynamicLeaf);

            assertEquals(group, dynamicLeaf.getParentGroup());

            dynamicLeaf.setSelected(true);
            assertTrue(group.isSelectedWithin());
        }

        @Test
        @DisplayName("should unbind listeners and clear parent on removal")
        void testDynamicSubItemRemoval() {
            var group = new SidebarGroup<String>("Group");
            var leaf = new SidebarNav<String>("Leaf");
            group.getItems().add(leaf);

            leaf.setSelected(true);
            assertTrue(group.isSelectedWithin());

            group.getItems().remove(leaf);

            assertNull(leaf.getParentGroup());
            assertFalse(group.isSelectedWithin());

            leaf.setSelected(true);
            assertFalse(group.isSelectedWithin());
        }
    }

    @Nested
    class LayoutAndCollapseTests {

        @Test
        @DisplayName("should update pseudo-class state on collapsed change")
        void testCollapsedPseudoClassState() {
            assertFalse(sidebar.isCollapsed());

            sidebar.setCollapsed(true);
            assertTrue(sidebar.isCollapsed());
            assertTrue(sidebar.getPseudoClassStates().stream()
                .anyMatch(pc -> pc.getPseudoClassName().equals("collapsed")));

            sidebar.setCollapsed(false);
            assertFalse(sidebar.getPseudoClassStates().stream()
                .anyMatch(pc -> pc.getPseudoClassName().equals("collapsed")));
        }
    }

    @Nested
    class BehaviorKeyboardTests {

        private SidebarBehavior<String> behavior;
        private SidebarSkin<String> skin;
        private SidebarNav<String> leaf1;
        private SidebarGroup<String> group;
        private SidebarNav<String> subLeaf;

        @BeforeEach
        void setupGraph() {
            leaf1 = new SidebarNav<>("Leaf 1");
            var header = new SidebarSection<String>("Header");
            group = new SidebarGroup<>("Group 1");
            subLeaf = new SidebarNav<>("Sub Leaf 1");
            group.getItems().add(subLeaf);

            sidebar.getItems().addAll(leaf1, header, group);

            skin = new SidebarSkin<>(sidebar);
            sidebar.setSkin(skin);
            behavior = skin.getBehavior();

            sidebar.requestFocus();
        }

        @Test
        @DisplayName("should skip non-focusable section headers on down key")
        void testDownKeyNavigationSkipsHeader() {
            var cellLeaf1 = skin.findCellForItem(leaf1);
            var cellGroup = skin.findCellForItem(group);

            behavior.setFocusedCell(cellLeaf1);
            assertEquals(cellLeaf1, behavior.getFocusedCell());

            behavior.handleKeyPress(createKeyEvent(KeyCode.DOWN));

            assertEquals(cellGroup, behavior.getFocusedCell());
        }

        @Test
        @DisplayName("should expand and collapse group on right and left keys")
        void testRightLeftKeyExpandsCollapsesGroup() {
            group.setExpanded(false);
            var cellGroup = skin.findCellForItem(group);
            behavior.setFocusedCell(cellGroup);

            behavior.handleKeyPress(createKeyEvent(KeyCode.RIGHT));
            assertTrue(group.isExpanded());

            behavior.handleKeyPress(createKeyEvent(KeyCode.LEFT));
            assertFalse(group.isExpanded());
        }

        @Test
        @DisplayName("should navigate to parent group on left key inside child")
        void testLeftKeyInSubItemNavigatesToParent() {
            group.setExpanded(true);

            var cellSubLeaf = skin.findCellForItem(subLeaf);
            var cellGroup = skin.findCellForItem(group);
            assertNotNull(cellSubLeaf);

            behavior.setFocusedCell(cellSubLeaf);

            behavior.handleKeyPress(createKeyEvent(KeyCode.LEFT));

            assertEquals(cellGroup, behavior.getFocusedCell());
        }

        @Test
        @DisplayName("should select leaf item and fire click event on enter")
        void testEnterKeySelectsLeafItemAndFiresEvent() {
            var cellLeaf1 = skin.findCellForItem(leaf1);
            behavior.setFocusedCell(cellLeaf1);

            boolean[] eventFired = {false};
            sidebar.addEventHandler(SidebarEvent.ITEM_CLICK, e -> {
                if (e.getItem() == leaf1) {
                    eventFired[0] = true;
                }
            });

            behavior.handleKeyPress(createKeyEvent(KeyCode.ENTER));

            assertEquals(leaf1, sidebar.getSelectionModel().getSelectedItem());
            assertTrue(eventFired[0]);
        }

        private KeyEvent createKeyEvent(KeyCode code) {
            return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "", "", code,
                false, false, false, false
            );
        }
    }

    @Nested
    class EventPropagationTests {

        @Test
        @DisplayName("should propagate text changed event through dispatcher")
        void testTextChangedEventPropagation() {
            var item = new SidebarNav<String>("Initial Text");
            boolean[] received = {false};

            item.addEventHandler(SidebarItemEvent.TEXT_CHANGED, e -> {
                assertEquals(item, e.getItem());
                received[0] = true;
            });

            item.setText("Updated Text");

            assertTrue(received[0]);
        }
    }
}