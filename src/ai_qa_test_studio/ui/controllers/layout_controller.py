"""Layout breakpoints, sidebar/drawer chrome, push vs overlay geometry."""

from __future__ import annotations

from typing import TYPE_CHECKING

from PySide6.QtCore import QEvent

from ai_qa_test_studio.constants import (
    UI_BREAKPOINT_MEDIUM,
    UI_BREAKPOINT_WIDE,
    UI_DRAWER_WIDTH,
    UI_DRAWER_WIDTH_COMPACT,
    UI_DRAWER_WIDTH_NARROW,
    UI_EDITOR_MIN_WIDTH,
    UI_EXPLORER_MIN_WIDTH,
    UI_SIDEBAR_MAX_RATIO,
    UI_SIDEBAR_WIDTH,
    UI_SIDEBAR_WIDTH_COMPACT,
    UI_SIDEBAR_WIDTH_MAX,
    UI_WIDGET_SIZE_MAX,
)
from ai_qa_test_studio.ui.widgets.right_drawer import TAB_COPILOT

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class LayoutController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window

    def apply_layout_breakpoint(self, width: int) -> None:
        # ≥960 push drawer; <960 overlay. Breakpoints also size chrome.
        w = self._w
        w._overlay_mode = width < UI_BREAKPOINT_MEDIUM
        if width >= UI_BREAKPOINT_WIDE:
            w._narrow_auto_collapsed = False
            sidebar = 0 if w._sidebar_collapsed else UI_SIDEBAR_WIDTH
            w._drawer_width = UI_DRAWER_WIDTH
        elif width >= UI_BREAKPOINT_MEDIUM:
            w._narrow_auto_collapsed = False
            sidebar = 0 if w._sidebar_collapsed else UI_SIDEBAR_WIDTH_COMPACT
            w._drawer_width = UI_DRAWER_WIDTH_COMPACT
        else:
            if not w._narrow_auto_collapsed:
                w._sidebar_collapsed = True
                w._narrow_auto_collapsed = True
            sidebar = 0 if w._sidebar_collapsed else UI_SIDEBAR_WIDTH_COMPACT
            w._drawer_width = UI_DRAWER_WIDTH_NARROW
        sidebar_cap = min(
            UI_SIDEBAR_WIDTH_MAX,
            max(UI_EXPLORER_MIN_WIDTH, int(width * UI_SIDEBAR_MAX_RATIO)),
        )
        if sidebar <= 0:
            w.explorer.hide()
        else:
            was_hidden = w.explorer.isHidden()
            w.explorer.show()
            w.explorer.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
            w.explorer.setMaximumWidth(sidebar_cap)
            w.explorer.apply_density(w.explorer.width() or sidebar)
            sizes = w.h_splitter.sizes()
            current = sizes[0] if len(sizes) >= 2 else 0
            if was_hidden or current < UI_EXPLORER_MIN_WIDTH or current > sidebar_cap:
                total = max(w.h_splitter.width(), sum(sizes), 1)
                target = min(sidebar, sidebar_cap)
                editor = max(UI_EDITOR_MIN_WIDTH, total - target)
                w.h_splitter.setSizes([target, editor])
        w.top_bar.apply_density(width)
        w.builder.apply_density(width)
        self.sync_drawer_geometry()

    def handle_resize_filter(self, watched: object, event: QEvent) -> bool:
        if event.type() != QEvent.Type.Resize:
            return False
        w = self._w
        if watched is getattr(w, "workspace", None):
            self.sync_drawer_geometry()
            return True
        if watched is getattr(w, "explorer", None):
            w.explorer.apply_density(w.explorer.width())
            return True
        return False

    def sync_drawer_geometry(self) -> None:
        w = self._w
        ww = w.workspace.width()
        wh = w.workspace.height()
        if ww <= 0 or wh <= 0:
            return
        layout = w.workspace.layout()
        if w.right_drawer.is_open:
            dw = min(w._drawer_width, max(1, ww))
            # Push: reserve right margin so h_splitter shrinks; overlay: full canvas.
            reserve = 0 if w._overlay_mode else dw
            if layout is not None:
                layout.setContentsMargins(0, 0, reserve, 0)
            x = max(0, ww - dw)
            w.right_drawer.setMinimumSize(0, 0)
            w.right_drawer.setMaximumSize(UI_WIDGET_SIZE_MAX, UI_WIDGET_SIZE_MAX)
            w.right_drawer.setGeometry(x, 0, dw, wh)
            w.right_drawer.show()
            w.right_drawer.raise_()
        else:
            if layout is not None:
                layout.setContentsMargins(0, 0, 0, 0)
            w.right_drawer.hide()

    def toggle_sidebar(self) -> None:
        w = self._w
        w._sidebar_collapsed = not w._sidebar_collapsed
        w.apply_layout_breakpoint(w.width())

    def toggle_copilot_drawer(self) -> None:
        w = self._w
        if w.right_drawer.is_open and w.right_drawer.stack.currentIndex() == 0:
            w.right_drawer.close_drawer()
        else:
            w.right_drawer.open_drawer(TAB_COPILOT)
        self.sync_drawer_geometry()

    def on_copilot_toggle(self, checked: bool) -> None:
        w = self._w
        if checked:
            w.right_drawer.open_drawer(TAB_COPILOT)
        else:
            w.right_drawer.close_drawer()
        self.sync_drawer_geometry()

    def open_copilot(self) -> None:
        w = self._w
        w.right_drawer.show_copilot(compose=True)
        w.top_bar.set_copilot_checked(True)
        self.sync_drawer_geometry()
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
            w._stop_browser()
            return
        if w.right_drawer.is_open:
            w.right_drawer.close_drawer()
            self.sync_drawer_geometry()

    def set_busy_chrome(self, busy: bool) -> None:
        w = self._w
        w.top_bar.set_running(busy)
        if busy:
            w.busy_bar.show()
            w.busy_bar.start()
        else:
            w.busy_bar.stop()
            w.busy_bar.hide()
