"""Fluent Dark theme + minimal QSS for QMainWindow shell (no FramelessWindow)."""

from __future__ import annotations

import sys

from PySide6.QtGui import QColor, QFont
from PySide6.QtWidgets import QApplication
from qfluentwidgets import Theme, setTheme, setThemeColor

from ai_qa_test_studio.constants import (
    THEME_ACCENT,
    THEME_BG_MAIN,
    THEME_BG_STATUS,
    THEME_BORDER,
    THEME_MUTED,
)

FLUENT_DARK_QSS = f"""
QMainWindow {{
    background-color: {THEME_BG_MAIN};
}}
QSplitter::handle {{
    background-color: {THEME_BORDER};
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
    background-color: {THEME_BG_STATUS};
    border-top: 1px solid {THEME_BORDER};
    color: {THEME_MUTED};
    padding: 2px 8px;
}}
QStatusBar::item {{
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
