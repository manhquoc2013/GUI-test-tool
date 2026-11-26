"""Layout breakpoints, 3-column splitter visibility, mutual exclusion."""

from __future__ import annotations

from typing import TYPE_CHECKING

from PySide6.QtCore import QEvent, QTimer

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
    UI_RESIZE_DEBOUNCE_MS,
    UI_SIDEBAR_WIDTH,
    UI_SIDEBAR_WIDTH_COMPACT,
    UI_SIDEBAR_WIDTH_MAX,
    UI_SPLITTER_PAINT_PAUSE_MS,
    UI_THREE_COL_SAFE,
)
from ai_qa_test_studio.ui.motion import (
    CHANNEL_WIDTH,
    animate_splitter_size,
    stop_channel,
    suspend_updates,
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
        self._sidebar_animating = False
        self._resize_timer = QTimer(window)
        self._resize_timer.setSingleShot(True)
        self._resize_timer.setInterval(UI_RESIZE_DEBOUNCE_MS)
        self._resize_timer.timeout.connect(self._flush_resize_breakpoint)
        self._pending_resize_w = 0
        self._splitter_paint_timer = QTimer(window)
        self._splitter_paint_timer.setSingleShot(True)
        self._splitter_paint_timer.setInterval(UI_SPLITTER_PAINT_PAUSE_MS)
        self._splitter_paint_timer.timeout.connect(self._resume_splitter_paint)

    def connect_splitter(self) -> None:
        self._w.h_splitter.splitterMoved.connect(self._on_splitter_moved)

    def schedule_resize_breakpoint(self, width: int) -> None:
        """Debounce MainWindow.resizeEvent so heavy panes do not reflow every frame."""
        self._pending_resize_w = width
        self._resize_timer.start()

    def _flush_resize_breakpoint(self) -> None:
        w = self._w
        with suspend_updates(w.workspace):
            self.apply_layout_breakpoint(self._pending_resize_w or w.width())

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

            if self._dock_animating or self._sidebar_animating:
                # Width driven by animation — keep current pane sizes, do not snap.
                sizes = w.h_splitter.sizes()
                show_explorer = explorer_visible or self._sidebar_animating
                show_dock = dock_visible or self._dock_animating
                w.explorer.setVisible(show_explorer)
                if show_dock:
                    w.right_drawer.show()
                self._restore_splitter_sizes(
                    sidebar_target,
                    show_explorer,
                    show_dock,
                    explorer_width_override=max(sizes[0], 0) if sizes else 0,
                    dock_width_override=(
                        max(sizes[2], 0) if len(sizes) > 2 else 0
                    ),
                )
                return

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
        *,
        dock_width_override: int | None = None,
        explorer_width_override: int | None = None,
    ) -> None:
        w = self._w
        total = max(w.h_splitter.width(), w.width(), 1)
        sizes = [0, 0, 0]
        if explorer_visible:
            if explorer_width_override is not None:
                sizes[0] = max(0, min(explorer_width_override, UI_SIDEBAR_WIDTH_MAX))
            else:
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
        if self._applying or self._dock_animating or self._sidebar_animating:
            return
        w = self._w
        if w.h_splitter.updatesEnabled():
            w.h_splitter.setUpdatesEnabled(False)
        self._splitter_paint_timer.start()
        sizes = w.h_splitter.sizes()
        if len(sizes) < 3:
            return
        if w.explorer.isVisible() and sizes[0] >= UI_EXPLORER_MIN_WIDTH:
            self._saved_explorer_w = sizes[0]
        if w.right_drawer.isVisible() and sizes[2] >= UI_DOCK_MIN_WIDTH:
            self._saved_dock_w = sizes[2]

    def _resume_splitter_paint(self) -> None:
        splitter = self._w.h_splitter
        if not splitter.updatesEnabled():
            splitter.setUpdatesEnabled(True)

    def handle_resize_filter(self, watched: object, event: QEvent) -> bool:
        if event.type() != QEvent.Type.Resize:
            return False
        w = self._w
        if watched is getattr(w, "explorer", None):
            # Density re-measures the pivot (text + adjustSize) — skip per-frame
            # work while the sidebar is sliding, then apply once in _on_done.
            if w.explorer.isVisible() and not self._sidebar_animating:
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
            self._stop_dock_anim()
            w.right_drawer.close_drawer()
            w.top_bar.set_copilot_checked(False)
            w.right_drawer.setMinimumWidth(0)
            w.right_drawer.setMaximumWidth(0)
        w._prefer_dock = False
        if opening:
            self._animate_open_sidebar()
        else:
            self._animate_close_sidebar()

    def _open_dock_policy(self) -> None:
        w = self._w
        w._prefer_dock = True
        if w.width() < UI_BREAKPOINT_WIDE:
            w._sidebar_collapsed = True

    def _target_dock_width(self) -> int:
        w = self._w
        return max(UI_DOCK_MIN_WIDTH, min(self._saved_dock_w, w._drawer_width, UI_DOCK_MAX_WIDTH))

    def _target_sidebar_width(self) -> int:
        return max(
            UI_EXPLORER_MIN_WIDTH,
            min(self._saved_explorer_w, self._sidebar_target_for_width(), UI_SIDEBAR_WIDTH_MAX),
        )

    def _sidebar_target_for_width(self) -> int:
        width = self._w.width()
        if width >= UI_BREAKPOINT_WIDE:
            return UI_SIDEBAR_WIDTH
        return UI_SIDEBAR_WIDTH_COMPACT

    def _stop_sidebar_anim(self) -> None:
        w = self._w
        was_animating = self._sidebar_animating
        self._sidebar_animating = False
        stop_channel(w.explorer, CHANNEL_WIDTH)
        # Resize-filter density was skipped mid-flight; restore it for the width
        # the sidebar actually stopped at.
        if was_animating and w.explorer.isVisible():
            w.explorer.apply_density(w.explorer.width())

    def _stop_dock_anim(self) -> None:
        w = self._w
        self._dock_animating = False
        stop_channel(w.right_drawer, CHANNEL_WIDTH)

    def _animate_open_sidebar(self) -> None:
        w = self._w
        self._stop_sidebar_anim()
        w._sidebar_collapsed = False
        target = self._target_sidebar_width()
        self._sidebar_animating = True
        self._applying = True
        try:
            dock_visible = w.right_drawer.is_open
            w.builder.setMinimumWidth(UI_EDITOR_MIN_WIDTH)
            w.explorer.setMinimumWidth(0)
            w.explorer.setMaximumWidth(UI_SIDEBAR_WIDTH_MAX)
            w.explorer.show()
            if dock_visible:
                w.right_drawer.setMinimumWidth(UI_DOCK_MIN_WIDTH)
                w.right_drawer.setMaximumWidth(UI_DOCK_MAX_WIDTH)
            else:
                w.right_drawer.setMinimumWidth(0)
                w.right_drawer.setMaximumWidth(0)
            self._restore_splitter_sizes(
                self._sidebar_target_for_width(),
                explorer_visible=True,
                dock_visible=dock_visible,
                explorer_width_override=0,
            )
        finally:
            self._applying = False

        def _on_done() -> None:
            self._sidebar_animating = False
            w.explorer.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
            w.explorer.setMaximumWidth(UI_SIDEBAR_WIDTH_MAX)
            self.apply_layout_breakpoint(w.width())

        animate_splitter_size(
            w.h_splitter,
            0,
            0,
            target,
            host=w.explorer,
            duration_ms=UI_MOTION_NORMAL_MS,
            on_done=_on_done,
        )

    def _animate_close_sidebar(self) -> None:
        w = self._w
        if w._sidebar_collapsed and not w.explorer.isVisible():
            return
        self._stop_sidebar_anim()
        sizes = w.h_splitter.sizes()
        start = sizes[0] if sizes else self._target_sidebar_width()
        if start >= UI_EXPLORER_MIN_WIDTH:
            self._saved_explorer_w = start
        # Keep logically open until animation ends so mid-flight resize does not snap-hide.
        self._sidebar_animating = True
        w.explorer.setMinimumWidth(0)
        w.explorer.setMaximumWidth(UI_SIDEBAR_WIDTH_MAX)

        def _on_done() -> None:
            self._sidebar_animating = False
            w._sidebar_collapsed = True
            w.explorer.setMinimumWidth(0)
            w.explorer.setMaximumWidth(0)
            w.explorer.hide()
            self.apply_layout_breakpoint(w.width())

        animate_splitter_size(
            w.h_splitter,
            0,
            start,
            0,
            host=w.explorer,
            duration_ms=UI_MOTION_NORMAL_MS,
            on_done=_on_done,
        )

    def _animate_open_dock(self, tab: str = TAB_COPILOT, *, compose: bool = False) -> None:
        w = self._w
        self._stop_sidebar_anim()
        self._stop_dock_anim()
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
            # Snap to constraint-aware sizes — animation must not leave explorer
            # wider than maximumWidth (dead gap between editor and dock).
            self.apply_layout_breakpoint(w.width())

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
        self._stop_sidebar_anim()
        self._stop_dock_anim()
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
        if hasattr(w, "set_mcp_warmup"):
            w.set_mcp_warmup(busy)
        if busy:
            w.busy_bar.show()
            w.busy_bar.start()
        else:
            w.busy_bar.stop()
            w.busy_bar.hide()
