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
    UI_MOTION_NORMAL_MS,
    UI_SIDEBAR_WIDTH,
    UI_SIDEBAR_WIDTH_COMPACT,
    UI_SIDEBAR_WIDTH_MAX,
    UI_THREE_COL_SAFE,
)
from ai_qa_test_studio.ui.motion import (
    CHANNEL_WIDTH,
    animate_splitter_size,
    stop_channel,
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
        self._dock_animating = False

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

            if self._dock_animating:
                # Keep explorer/editor constraints; dock width driven by animation.
                w.explorer.setVisible(explorer_visible)
                self._restore_splitter_sizes(
                    sidebar_target,
                    explorer_visible,
                    dock_visible=True,
                    dock_width_override=max(w.h_splitter.sizes()[2] if len(w.h_splitter.sizes()) > 2 else 0, 0),
                )
                return

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
        *,
        dock_width_override: int | None = None,
    ) -> None:
        w = self._w
        total = max(w.h_splitter.width(), w.width(), 1)
        sizes = [0, 0, 0]
        if explorer_visible:
            preferred = sidebar_target or self._saved_explorer_w
            sizes[0] = min(max(self._saved_explorer_w, UI_EXPLORER_MIN_WIDTH), preferred)
            sizes[0] = max(UI_EXPLORER_MIN_WIDTH, min(sizes[0], UI_SIDEBAR_WIDTH_MAX))
        if dock_visible:
            if dock_width_override is not None:
                sizes[2] = max(0, min(dock_width_override, UI_DOCK_MAX_WIDTH))
            else:
                sizes[2] = max(UI_DOCK_MIN_WIDTH, min(self._saved_dock_w, w._drawer_width))
        sizes[1] = max(UI_EDITOR_MIN_WIDTH, total - sizes[0] - sizes[2])
        w.h_splitter.setSizes(sizes)

    def _on_splitter_moved(self, _pos: int = 0, _index: int = 0) -> None:
        if self._applying or self._dock_animating:
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
            # Mutual-exclusion swap snaps dock closed (explorer takes the slot).
            # Stop in-flight width anim so open/close on_done cannot resurrect dock.
            w._prefer_dock = False
            self._dock_animating = False
            stop_channel(w.right_drawer, CHANNEL_WIDTH)
            w.right_drawer.close_drawer()
            w.top_bar.set_copilot_checked(False)
            w.right_drawer.setMinimumWidth(0)
            w.right_drawer.setMaximumWidth(0)
        w._sidebar_collapsed = not w._sidebar_collapsed
        w._prefer_dock = False
        self.apply_layout_breakpoint(w.width())

    def _open_dock_policy(self) -> None:
        w = self._w
        w._prefer_dock = True
        if w.width() < UI_BREAKPOINT_WIDE:
            w._sidebar_collapsed = True

    def _target_dock_width(self) -> int:
        w = self._w
        return max(UI_DOCK_MIN_WIDTH, min(self._saved_dock_w, w._drawer_width, UI_DOCK_MAX_WIDTH))

    def _sidebar_target_for_width(self) -> int:
        width = self._w.width()
        if width >= UI_BREAKPOINT_WIDE:
            return UI_SIDEBAR_WIDTH
        return UI_SIDEBAR_WIDTH_COMPACT

    def _animate_open_dock(self, tab: str = TAB_COPILOT, *, compose: bool = False) -> None:
        w = self._w
        was_open = w.right_drawer.is_open
        sizes_before = list(w.h_splitter.sizes())
        start = (
            max(sizes_before[2], 0)
            if was_open and len(sizes_before) > 2
            else 0
        )
        w.right_drawer.open_drawer(tab, compose=compose)
        w.top_bar.set_copilot_checked(True)
        # Apply explorer collapse instantly; dock width driven by animation.
        self._dock_animating = True
        self._applying = True
        try:
            explorer_visible = not w._sidebar_collapsed
            w.builder.setMinimumWidth(UI_EDITOR_MIN_WIDTH)
            if explorer_visible:
                w.explorer.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
                w.explorer.setMaximumWidth(UI_SIDEBAR_WIDTH_MAX)
            else:
                w.explorer.setMinimumWidth(0)
                w.explorer.setMaximumWidth(0)
            w.explorer.setVisible(explorer_visible)
            # Unlock: min=0 so setSizes can slide from 0 (do NOT set UI_DOCK_MIN_WIDTH yet).
            w.right_drawer.setMinimumWidth(0)
            w.right_drawer.setMaximumWidth(UI_DOCK_MAX_WIDTH)
            w.right_drawer.show()
            if start == 0:
                self._restore_splitter_sizes(
                    self._sidebar_target_for_width(),
                    explorer_visible,
                    dock_visible=True,
                    dock_width_override=0,
                )
        finally:
            self._applying = False

        def _on_done() -> None:
            self._dock_animating = False
            w.right_drawer.setMinimumWidth(UI_DOCK_MIN_WIDTH)
            w.right_drawer.setMaximumWidth(UI_DOCK_MAX_WIDTH)
            w.top_bar.set_copilot_checked(True)

        animate_splitter_size(
            w.h_splitter,
            2,
            start,
            self._target_dock_width(),
            host=w.right_drawer,
            duration_ms=UI_MOTION_NORMAL_MS,
            on_done=_on_done,
        )

    def _animate_close_dock(self) -> None:
        w = self._w
        if not w.right_drawer.is_open and not w.right_drawer.isVisible():
            return
        sizes = w.h_splitter.sizes()
        start = sizes[2] if len(sizes) > 2 else self._target_dock_width()
        if start >= UI_DOCK_MIN_WIDTH:
            self._saved_dock_w = start
        self._dock_animating = True
        # Keep logical open until animation ends so mid-flight resize does not snap-hide.
        w.right_drawer.setMinimumWidth(0)
        w.right_drawer.setMaximumWidth(UI_DOCK_MAX_WIDTH)

        def _on_done() -> None:
            self._dock_animating = False
            w.right_drawer.close_drawer()
            w.top_bar.set_copilot_checked(False)
            w.right_drawer.setMinimumWidth(0)
            w.right_drawer.setMaximumWidth(0)
            self.apply_layout_breakpoint(w.width())

        animate_splitter_size(
            w.h_splitter,
            2,
            start,
            0,
            host=w.right_drawer,
            duration_ms=UI_MOTION_NORMAL_MS,
            on_done=_on_done,
        )

    def toggle_copilot_drawer(self) -> None:
        w = self._w
        if w.right_drawer.is_open and w.right_drawer.stack.currentIndex() == 0:
            self._animate_close_dock()
        else:
            self._open_dock_policy()
            self._animate_open_dock(TAB_COPILOT)

    def on_copilot_toggle(self, checked: bool) -> None:
        if checked:
            self._open_dock_policy()
            self._animate_open_dock(TAB_COPILOT)
        else:
            self._animate_close_dock()

    def open_copilot(self) -> None:
        w = self._w
        self._open_dock_policy()
        self._animate_open_dock(TAB_COPILOT, compose=True)
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
            self._animate_close_dock()

    def set_busy_chrome(self, busy: bool) -> None:
        w = self._w
        w.top_bar.set_running(busy)
        if busy:
            w.busy_bar.show()
            w.busy_bar.start()
        else:
            w.busy_bar.stop()
            w.busy_bar.hide()
