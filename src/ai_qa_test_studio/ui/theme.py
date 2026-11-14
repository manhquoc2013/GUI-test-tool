"""Fluent theme — v4.2 Dark/Light surface elevation helpers."""

from __future__ import annotations

import sys

from PySide6.QtGui import QColor, QFont
from PySide6.QtWidgets import QApplication
from qfluentwidgets import Theme, isDarkTheme, setTheme, setThemeColor

from ai_qa_test_studio.constants import (
    THEME_ACCENT,
    THEME_ACTION_BADGE_BG_ALPHA,
    THEME_BG_CANVAS,
    THEME_BG_ELEVATED,
    THEME_BG_SURFACE,
    THEME_BORDER_SUBTLE,
    THEME_CARD_RADIUS,
    THEME_SURFACE_BORDER_ALPHA,
    THEME_SURFACE_CARD_ALPHA,
    THEME_SURFACE_PANEL_ALPHA,
    THEME_TEXT_MUTED,
    THEME_TEXT_ON_LIGHT,
    THEME_TEXT_PRIMARY,
    THEME_UNBOUND_BG_ALPHA,
    THEME_UNBOUND_BORDER_ALPHA,
)


def _rgba(channel: int, alpha: float) -> str:
    return f"rgba({channel}, {channel}, {channel}, {alpha})"


def elevation_channel(*, dark: bool | None = None) -> int:
    use_dark = isDarkTheme() if dark is None else dark
    return 255 if use_dark else 0


def surface_panel_css(*, dark: bool | None = None) -> str:
    return _rgba(elevation_channel(dark=dark), THEME_SURFACE_PANEL_ALPHA)


def surface_card_css(*, dark: bool | None = None) -> str:
    return _rgba(elevation_channel(dark=dark), THEME_SURFACE_CARD_ALPHA)


def surface_border_css(*, dark: bool | None = None) -> str:
    return _rgba(elevation_channel(dark=dark), THEME_SURFACE_BORDER_ALPHA)


def unbound_banner_bg_css(*, dark: bool | None = None) -> str:
    return f"rgba(245, 158, 11, {THEME_UNBOUND_BG_ALPHA:.2f})"


def unbound_banner_border_css(*, dark: bool | None = None) -> str:
    return f"rgba(245, 158, 11, {THEME_UNBOUND_BORDER_ALPHA:.2f})"


def text_primary_css(*, dark: bool | None = None) -> str:
    """Primary body/title text for current (or forced) theme."""
    use_dark = isDarkTheme() if dark is None else dark
    return THEME_TEXT_PRIMARY if use_dark else THEME_TEXT_ON_LIGHT


def action_badge_bg_css(fg_hex: str) -> str:
    """Build translucent badge background from a #RRGGBB foreground."""
    hex_color = fg_hex.lstrip("#")
    if len(hex_color) != 6:
        return f"rgba(148, 163, 184, {THEME_ACTION_BADGE_BG_ALPHA})"
    r = int(hex_color[0:2], 16)
    g = int(hex_color[2:4], 16)
    b = int(hex_color[4:6], 16)
    return f"rgba({r}, {g}, {b}, {THEME_ACTION_BADGE_BG_ALPHA})"


def panel_stylesheet(object_name: str, *, border_left: bool = False) -> str:
    border = (
        f"border-left: 1px solid {surface_border_css()};"
        if border_left
        else f"border: 1px solid {surface_border_css()};"
    )
    return (
        f"#{object_name} {{"
        f"background-color: {surface_panel_css()};"
        f"{border}"
        f"}}"
    )


def card_stylesheet(object_name: str) -> str:
    return (
        f"{object_name} {{"
        f"background-color: {surface_card_css()};"
        f"border: 1px solid {surface_border_css()};"
        f"border-radius: 10px;"
        f"}}"
    )


FLUENT_DARK_QSS = f"""
QMainWindow {{
    background-color: {THEME_BG_CANVAS};
    color: {THEME_TEXT_PRIMARY};
}}
CardWidget {{
    background-color: {THEME_BG_SURFACE};
    border: none;
    border-radius: {THEME_CARD_RADIUS}px;
}}
QSplitter::handle {{
    background-color: {THEME_BORDER_SUBTLE};
}}
QSplitter::handle:horizontal {{
    width: 2px;
}}
QSplitter::handle:vertical {{
    height: 6px;
}}
QSplitter::handle:hover {{
    background-color: {THEME_ACCENT};
}}
QStatusBar {{
    background-color: {THEME_BG_CANVAS};
    border-top: 1px solid {THEME_BORDER_SUBTLE};
    color: {THEME_TEXT_MUTED};
    padding: 2px 8px;
    min-height: 28px;
    max-height: 28px;
}}
QStatusBar::item {{
    border: none;
}}
QTreeWidget {{
    background-color: {THEME_BG_SURFACE};
    border: none;
    outline: none;
}}
QTreeWidget::item {{
    min-height: 34px;
    padding: 4px 8px;
    border-radius: 6px;
}}
QTreeWidget::item:hover {{
    background-color: {THEME_BG_ELEVATED};
}}
QListWidget {{
    background-color: {THEME_BG_SURFACE};
    border: none;
    outline: none;
}}
QScrollArea {{
    background-color: transparent;
    border: none;
}}
"""


def _platform_ui_font() -> QFont:
    if sys.platform == "darwin":
        return QFont(".AppleSystemUIFont", 13)
    if sys.platform.startswith("win"):
        return QFont("Segoe UI", 13)
    return QFont("Sans Serif", 13)


def apply_studio_theme(app: QApplication) -> None:
    setTheme(Theme.DARK)
    setThemeColor(QColor(THEME_ACCENT))
    app.setFont(_platform_ui_font())
    app.setStyleSheet(FLUENT_DARK_QSS)


def apply_theme(app: QApplication, theme: str = "dark") -> None:
    """Backward-compatible entry used by ``__main__``."""
    if theme != "dark":
        setTheme(Theme.LIGHT)
        app.setStyleSheet("")
        return
    apply_studio_theme(app)
