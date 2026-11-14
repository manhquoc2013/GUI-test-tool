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
        sidebar_target = self._update_breakpoint_chrome(width)
        self._resolve_pane_flags(width)
        self._apply_pane_visibility(sidebar_target)
        w.top_bar.apply_density(width)
        w.builder.apply_density(width)
        if hasattr(w.explorer, "apply_density") and not w._sidebar_collapsed:
            w.explorer.apply_density(w.explorer.width() or self._saved_explorer_w)

    def _update_breakpoint_chrome(self, width: int) -> int:
        w = self._w
        if width >= UI_BREAKPOINT_WIDE:
            w._narrow_auto_collapsed = False
            w._drawer_width = UI_DRAWER_WIDTH
            return UI_SIDEBAR_WIDTH
        if width >= UI_BREAKPOINT_MEDIUM:
            w._narrow_auto_collapsed = False
            w._drawer_width = UI_DRAWER_WIDTH_COMPACT
            return UI_SIDEBAR_WIDTH_COMPACT
        if not w._narrow_auto_collapsed:
            w._sidebar_collapsed = True
            w._narrow_auto_collapsed = True
        w._drawer_width = UI_DRAWER_WIDTH_NARROW
        return UI_SIDEBAR_WIDTH_COMPACT

    def _resolve_pane_flags(self, width: int) -> None:
        """Apply mutual exclusion only below UI_THREE_COL_SAFE.

        Open-dock auto-hide of Explorer is handled in open_* APIs, not on every
        resize — so ☰ can re-open Explorer while Copilot stays open in the
        laptop band (1150 ≤ W < 1440).
        """
        w = self._w
        want_explorer = not w._sidebar_collapsed
        want_dock = w.right_drawer.is_open
        if not (want_explorer and want_dock and width < UI_THREE_COL_SAFE):
            return
        if getattr(w, "_prefer_dock", False):
            w._sidebar_collapsed = True
        elif w.right_drawer.is_open:
            w.right_drawer.close_drawer()
            w.top_bar.set_copilot_checked(False)

    def _apply_pane_visibility(self, sidebar_target: int) -> None:
        w = self._w
        self._applying = True
        try:
            explorer_visible = not w._sidebar_collapsed
            dock_visible = w.right_drawer.is_open

            w.builder.setMinimumWidth(UI_EDITOR_MIN_WIDTH)

            if explorer_visible:
                w.explorer.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
                w.explorer.setMaximumWidth(UI_SIDEBAR_WIDTH_MAX)
            else:
                w.explorer.setMinimumWidth(0)
                w.explorer.setMaximumWidth(0)

            if dock_visible:
                w.right_drawer.setMinimumWidth(UI_DOCK_MIN_WIDTH)
                w.right_drawer.setMaximumWidth(UI_DOCK_MAX_WIDTH)
            else:
                w.right_drawer.setMinimumWidth(0)
                w.right_drawer.setMaximumWidth(0)

            w.explorer.setVisible(explorer_visible)
            w.right_drawer.setVisible(dock_visible)

            self._restore_splitter_sizes(sidebar_target, explorer_visible, dock_visible)
        finally:
            self._applying = False

    def _restore_splitter_sizes(
        self,
        sidebar_target: int,
        explorer_visible: bool,
        dock_visible: bool,
    ) -> None:
        w = self._w
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
        """Restore splitter sizes only — never flip open/collapsed policy."""
        w = self._w
        target = UI_SIDEBAR_WIDTH if not w._sidebar_collapsed else 0
        if w.width() >= UI_BREAKPOINT_MEDIUM and w.width() < UI_BREAKPOINT_WIDE:
            target = UI_SIDEBAR_WIDTH_COMPACT if not w._sidebar_collapsed else 0
        elif w.width() < UI_BREAKPOINT_MEDIUM:
            target = UI_SIDEBAR_WIDTH_COMPACT if not w._sidebar_collapsed else 0
        self._applying = True
        try:
            self._restore_splitter_sizes(
                target,
                explorer_visible=not w._sidebar_collapsed,
                dock_visible=w.right_drawer.is_open,
            )
        finally:
            self._applying = False

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

    def _open_dock_policy(self) -> None:
        w = self._w
        w._prefer_dock = True
        if w.width() < UI_BREAKPOINT_WIDE:
            w._sidebar_collapsed = True

    def toggle_copilot_drawer(self) -> None:
        w = self._w
        if w.right_drawer.is_open and w.right_drawer.stack.currentIndex() == 0:
            w.right_drawer.close_drawer()
        else:
            self._open_dock_policy()
            w.right_drawer.open_drawer(TAB_COPILOT)
        self.apply_layout_breakpoint(w.width())

    def on_copilot_toggle(self, checked: bool) -> None:
        w = self._w
        if checked:
            self._open_dock_policy()
            w.right_drawer.open_drawer(TAB_COPILOT)
        else:
            w.right_drawer.close_drawer()
        self.apply_layout_breakpoint(w.width())

    def open_copilot(self) -> None:
        w = self._w
        self._open_dock_policy()
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
