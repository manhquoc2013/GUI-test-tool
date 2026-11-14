"""ScrollPerPixel + SmoothScrollDelegate + smart auto-scroll for live feeds.

On macOS, trackpad already delivers inertial pixelDelta at display refresh.
Attaching SmoothScrollDelegate there double-applies acceleration curves and
causes frame stutter — so the delegate is Windows/Linux only.
"""

from __future__ import annotations

import sys
from collections.abc import Callable
from typing import Any

from PySide6.QtCore import QObject, Qt, Signal
from PySide6.QtWidgets import (
    QAbstractItemView,
    QAbstractScrollArea,
    QPushButton,
    QScrollArea,
    QScrollBar,
    QWidget,
)
from qfluentwidgets import SmoothScrollDelegate

from ai_qa_test_studio.constants import (
    UI_JUMP_LATEST_LABEL,
    UI_SCROLL_NEAR_BOTTOM_PX,
)

_USE_SMOOTH_SCROLL_DELEGATE = sys.platform != "darwin"


def uses_smooth_scroll_delegate() -> bool:
    """True when SmoothScrollDelegate is attached (non-macOS)."""
    return _USE_SMOOTH_SCROLL_DELEGATE


def apply_pixel_scroll(
    view: QAbstractItemView,
    *,
    horizontal: bool = True,
    vertical: bool = True,
    smooth: bool = True,
) -> SmoothScrollDelegate | None:
    """Enable ScrollPerPixel; attach SmoothScrollDelegate only off macOS."""
    if vertical:
        view.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
    if horizontal:
        view.setHorizontalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
    if not smooth or not _USE_SMOOTH_SCROLL_DELEGATE:
        return None
    return SmoothScrollDelegate(view, useAni=True)


def apply_scroll_area_smooth(area: QAbstractScrollArea) -> SmoothScrollDelegate | None:
    """Attach Fluent SmoothScrollDelegate off macOS; no-op on darwin."""
    if not _USE_SMOOTH_SCROLL_DELEGATE:
        return None
    return SmoothScrollDelegate(area, useAni=True)


def create_scroll_area(parent: QWidget | None = None) -> QScrollArea:
    """Plain QScrollArea; SmoothScrollDelegate only on Windows/Linux."""
    area = QScrollArea(parent)
    apply_scroll_area_smooth(area)
    return area


def _bar_near_bottom(bar: QScrollBar, *, threshold_px: int = UI_SCROLL_NEAR_BOTTOM_PX) -> bool:
    remaining = bar.maximum() - bar.value()
    return remaining <= max(0, threshold_px)


class SmartAutoScroller(QObject):
    """Auto-scroll to bottom unless the user scrolls up to read history.

    Shows a jump-to-latest control while following is paused.
    """

    following_changed = Signal(bool)

    def __init__(
        self,
        scroll_area: QAbstractScrollArea,
        *,
        jump_button: QWidget | None = None,
        threshold_px: int = UI_SCROLL_NEAR_BOTTOM_PX,
        parent: QObject | None = None,
    ) -> None:
        super().__init__(parent or scroll_area)
        self._area = scroll_area
        self._threshold_px = threshold_px
        self._following = True
        self._programmatic = False
        self._jump = jump_button
        bar = scroll_area.verticalScrollBar()
        bar.valueChanged.connect(self._on_value_changed)
        bar.rangeChanged.connect(self._on_range_changed)
        if self._jump is not None:
            self._jump.hide()
            if isinstance(self._jump, QPushButton):
                self._jump.setText(UI_JUMP_LATEST_LABEL)
            clicked = getattr(self._jump, "clicked", None)
            if clicked is not None:
                clicked.connect(self.scroll_to_bottom)

    @property
    def following(self) -> bool:
        return self._following

    def set_following(self, enabled: bool) -> None:
        if self._following == enabled:
            self._sync_jump()
            return
        self._following = enabled
        self._sync_jump()
        self.following_changed.emit(enabled)

    def scroll_to_bottom(self) -> None:
        bar = self._area.verticalScrollBar()
        self._programmatic = True
        try:
            bar.setValue(bar.maximum())
        finally:
            self._programmatic = False
        self.set_following(True)

    def on_content_appended(self) -> None:
        if self._following:
            self.scroll_to_bottom()
        else:
            self._sync_jump()

    def _on_value_changed(self, _value: int) -> None:
        if self._programmatic:
            return
        bar = self._area.verticalScrollBar()
        if bar.maximum() <= 0:
            self.set_following(True)
            return
        near = _bar_near_bottom(bar, threshold_px=self._threshold_px)
        self.set_following(near)

    def _on_range_changed(self, _min_v: int, _max_v: int) -> None:
        if self._following:
            self.scroll_to_bottom()

    def _sync_jump(self) -> None:
        if self._jump is None:
            return
        show = not self._following
        self._jump.setVisible(show)


def make_jump_button(parent: QWidget | None = None) -> QPushButton:
    btn = QPushButton(UI_JUMP_LATEST_LABEL, parent)
    btn.setObjectName("JumpToLatestButton")
    btn.setCursor(Qt.CursorShape.PointingHandCursor)
    btn.setAccessibleName(UI_JUMP_LATEST_LABEL)
    btn.setFlat(False)
    btn.hide()
    return btn


def style_jump_button(btn: QPushButton, *, dark: bool | None = None) -> None:
    """High-contrast jump control — never white-on-white / white-on-light."""
    from qfluentwidgets import isDarkTheme

    from ai_qa_test_studio.constants import (
        THEME_ACCENT,
        UI_JUMP_LATEST_BORDER_PX,
        UI_JUMP_LATEST_FG_DARK,
        UI_JUMP_LATEST_FG_LIGHT,
        UI_JUMP_LATEST_PADDING_H,
        UI_JUMP_LATEST_PADDING_V,
        UI_JUMP_LATEST_RADIUS,
    )
    from ai_qa_test_studio.ui.theme import get_theme_tokens

    use_dark = isDarkTheme() if dark is None else dark
    t = get_theme_tokens(dark=use_dark)
    fg = UI_JUMP_LATEST_FG_DARK if use_dark else UI_JUMP_LATEST_FG_LIGHT
    bg = THEME_ACCENT if use_dark else t.bg_card
    border = t.border_card if not use_dark else THEME_ACCENT
    btn.setStyleSheet(
        "#JumpToLatestButton {"
        f"background-color: {bg};"
        f"color: {fg};"
        f"border: {UI_JUMP_LATEST_BORDER_PX}px solid {border};"
        f"border-radius: {UI_JUMP_LATEST_RADIUS}px;"
        f"padding: {UI_JUMP_LATEST_PADDING_V}px {UI_JUMP_LATEST_PADDING_H}px;"
        "font-weight: 700;"
        "}"
        "#JumpToLatestButton:hover {"
        f"background-color: {THEME_ACCENT};"
        f"color: {UI_JUMP_LATEST_FG_DARK};"
        f"border-color: {THEME_ACCENT};"
        "}"
    )


def wire_list_auto_scroll(
    list_view: QAbstractItemView,
    *,
    jump_button: QWidget | None = None,
) -> SmartAutoScroller:
    """Smart auto-scroll for QListWidget / similar item views."""
    apply_pixel_scroll(list_view)
    return SmartAutoScroller(list_view, jump_button=jump_button)


def wire_scroll_area_auto_scroll(
    area: QScrollArea,
    *,
    jump_button: QWidget | None = None,
) -> SmartAutoScroller:
    apply_scroll_area_smooth(area)
    return SmartAutoScroller(area, jump_button=jump_button)


def batch_updates(widget: QWidget, mutate: Callable[[], Any]) -> Any:
    """Disable painting while applying a heavy batch of child updates."""
    widget.setUpdatesEnabled(False)
    try:
        return mutate()
    finally:
        widget.setUpdatesEnabled(True)


__all__ = [
    "SmartAutoScroller",
    "apply_pixel_scroll",
    "apply_scroll_area_smooth",
    "batch_updates",
    "create_scroll_area",
    "make_jump_button",
    "uses_smooth_scroll_delegate",
    "wire_list_auto_scroll",
    "wire_scroll_area_auto_scroll",
]
