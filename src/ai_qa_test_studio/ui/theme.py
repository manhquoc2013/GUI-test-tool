"""Fluent Dark theme — Airy v4.1 surface elevation over heavy borders."""

from __future__ import annotations

import sys

from PySide6.QtGui import QColor, QFont
from PySide6.QtWidgets import QApplication
from qfluentwidgets import Theme, setTheme, setThemeColor

from ai_qa_test_studio.constants import (
    THEME_ACCENT,
    THEME_BG_CANVAS,
    THEME_BG_ELEVATED,
    THEME_BG_SURFACE,
    THEME_BORDER_SUBTLE,
    THEME_CARD_RADIUS,
    THEME_TEXT_MUTED,
    THEME_TEXT_PRIMARY,
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
    width: 1px;
}}
QSplitter::handle:vertical {{
    height: 1px;
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
        app.setStyleSheet("")
        return
    apply_studio_theme(app)
