"""Layout breakpoints, 3-column splitter visibility, mutual exclusion."""

from __future__ import annotations

from typing import TYPE_CHECKING

from PySide6.QtCore import QEvent

from ai_qa_test_studio.constants import (
    UI_BREAKPOINT_MEDIUM,
    UI_BREAKPOINT_WIDE,
    UI_DOCK_MAX_WIDTH,
    UI_DOCK_MIN_WIDTH,
    UI_DRAWER_WIDTH,
    UI_DRAWER_WIDTH_COMPACT,
    UI_DRAWER_WIDTH_NARROW,
    UI_EDITOR_MIN_WIDTH,
    UI_EXPLORER_MIN_WIDTH,
    UI_SIDEBAR_WIDTH,
    UI_SIDEBAR_WIDTH_COMPACT,
    UI_SIDEBAR_WIDTH_MAX,
    UI_THREE_COL_SAFE,
)
from ai_qa_test_studio.ui.widgets.right_drawer import TAB_COPILOT

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class LayoutController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window
        self._saved_explorer_w = UI_SIDEBAR_WIDTH
        self._saved_dock_w = UI_DRAWER_WIDTH
        self._applying = False

    def connect_splitter(self) -> None:
        self._w.h_splitter.splitterMoved.connect(self._on_splitter_moved)

    def apply_layout_breakpoint(self, width: int) -> None:
        w = self._w
        if width >= UI_BREAKPOINT_WIDE:
            w._narrow_auto_collapsed = False
            w._drawer_width = UI_DRAWER_WIDTH
            sidebar_target = UI_SIDEBAR_WIDTH
        elif width >= UI_BREAKPOINT_MEDIUM:
            w._narrow_auto_collapsed = False
            w._drawer_width = UI_DRAWER_WIDTH_COMPACT
            sidebar_target = UI_SIDEBAR_WIDTH_COMPACT
        else:
            if not w._narrow_auto_collapsed:
                w._sidebar_collapsed = True
                w._narrow_auto_collapsed = True
            w._drawer_width = UI_DRAWER_WIDTH_NARROW
            sidebar_target = UI_SIDEBAR_WIDTH_COMPACT

        if w.right_drawer.is_open and width < UI_BREAKPOINT_WIDE and not w._sidebar_collapsed:
            w._sidebar_collapsed = True

        self._apply_pane_visibility(sidebar_target)
        w.top_bar.apply_density(width)
        w.builder.apply_density(width)
        if hasattr(w.explorer, "apply_density"):
            exp_w = self._saved_explorer_w if not w._sidebar_collapsed else 0
            if exp_w > 0:
                w.explorer.apply_density(exp_w)

    def _apply_pane_visibility(self, sidebar_target: int) -> None:
        w = self._w
        self._applying = True
        try:
            explorer_visible = not w._sidebar_collapsed
            dock_visible = w.right_drawer.is_open

            if explorer_visible and dock_visible and w.width() < UI_THREE_COL_SAFE:
                # Mutual exclusion under safe width — prefer dock if it was just opened.
                if getattr(w, "_prefer_dock", False):
                    explorer_visible = False
                    w._sidebar_collapsed = True
                else:
                    dock_visible = False
                    w.right_drawer.close_drawer()

            w.explorer.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
            w.explorer.setMaximumWidth(UI_SIDEBAR_WIDTH_MAX)
            w.right_drawer.setMinimumWidth(UI_DOCK_MIN_WIDTH)
            w.right_drawer.setMaximumWidth(UI_DOCK_MAX_WIDTH)
            w.builder.setMinimumWidth(UI_EDITOR_MIN_WIDTH)

            w.explorer.setVisible(explorer_visible)
            w.right_drawer.setVisible(dock_visible)

            total = max(w.h_splitter.width(), w.width(), 1)
            sizes = [0, 0, 0]
            if explorer_visible:
                preferred = sidebar_target or self._saved_explorer_w
                sizes[0] = min(max(self._saved_explorer_w, UI_EXPLORER_MIN_WIDTH), preferred)
                sizes[0] = max(UI_EXPLORER_MIN_WIDTH, min(sizes[0], UI_SIDEBAR_WIDTH_MAX))
            if dock_visible:
                sizes[2] = max(UI_DOCK_MIN_WIDTH, min(self._saved_dock_w, w._drawer_width))
            sizes[1] = max(UI_EDITOR_MIN_WIDTH, total - sizes[0] - sizes[2])
            w.h_splitter.setSizes(sizes)
        finally:
            self._applying = False

    def _on_splitter_moved(self, _pos: int = 0, _index: int = 0) -> None:
        if self._applying:
            return
        w = self._w
        sizes = w.h_splitter.sizes()
        if len(sizes) < 3:
            return
        if w.explorer.isVisible() and sizes[0] >= UI_EXPLORER_MIN_WIDTH:
            self._saved_explorer_w = sizes[0]
        if w.right_drawer.isVisible() and sizes[2] >= UI_DOCK_MIN_WIDTH:
            self._saved_dock_w = sizes[2]

    def handle_resize_filter(self, watched: object, event: QEvent) -> bool:
        if event.type() != QEvent.Type.Resize:
            return False
        w = self._w
        if watched is getattr(w, "explorer", None):
            if w.explorer.isVisible():
                w.explorer.apply_density(w.explorer.width())
            return True
        return False

    def sync_drawer_geometry(self) -> None:
        """Compatibility no-op — dock is a splitter child in v4.2."""
        self._apply_pane_visibility(
            UI_SIDEBAR_WIDTH if not self._w._sidebar_collapsed else 0
        )

    def toggle_sidebar(self) -> None:
        w = self._w
        opening = w._sidebar_collapsed
        if opening and w.right_drawer.is_open and w.width() < UI_THREE_COL_SAFE:
            w._prefer_dock = False
            w.right_drawer.close_drawer()
            w.top_bar.set_copilot_checked(False)
        w._sidebar_collapsed = not w._sidebar_collapsed
        w._prefer_dock = False
        self.apply_layout_breakpoint(w.width())

    def toggle_copilot_drawer(self) -> None:
        w = self._w
        if w.right_drawer.is_open and w.right_drawer.stack.currentIndex() == 0:
            w.right_drawer.close_drawer()
        else:
            w._prefer_dock = True
            if w.width() < UI_BREAKPOINT_WIDE:
                w._sidebar_collapsed = True
            w.right_drawer.open_drawer(TAB_COPILOT)
        self.apply_layout_breakpoint(w.width())

    def on_copilot_toggle(self, checked: bool) -> None:
        w = self._w
        if checked:
            w._prefer_dock = True
            if w.width() < UI_BREAKPOINT_WIDE:
                w._sidebar_collapsed = True
            w.right_drawer.open_drawer(TAB_COPILOT)
        else:
            w.right_drawer.close_drawer()
        self.apply_layout_breakpoint(w.width())

    def open_copilot(self) -> None:
        w = self._w
        w._prefer_dock = True
        if w.width() < UI_BREAKPOINT_WIDE:
            w._sidebar_collapsed = True
        w.right_drawer.show_copilot(compose=True)
        w.top_bar.set_copilot_checked(True)
        self.apply_layout_breakpoint(w.width())
        w._toast_info(
            "QA Copilot",
            "Tab AI Wizard (3 bước) nằm trong Copilot. Chat dùng Planner→Composer→Critic.",
        )

    def on_escape(self) -> None:
        w = self._w
        if w.copilot.is_busy:
            w._stop_copilot()
            return
        if w._busy:
            w.session.request_cancel()
            w._request_stop_browser()
            return
        if w.right_drawer.is_open:
            w.right_drawer.close_drawer()
            self.apply_layout_breakpoint(w.width())

    def set_busy_chrome(self, busy: bool) -> None:
        w = self._w
        w.top_bar.set_running(busy)
        if busy:
            w.busy_bar.show()
            w.busy_bar.start()
        else:
            w.busy_bar.stop()
            w.busy_bar.hide()
