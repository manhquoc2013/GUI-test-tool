"""QSS design tokens — load `theme.qss` (no QtWebEngine)."""

from __future__ import annotations

from pathlib import Path

_THEME_DIR = Path(__file__).resolve().parent
_DARK_QSS_PATH = _THEME_DIR / "theme.qss"

# Minimal light fallback if file missing (tests / broken install)
_LIGHT_FALLBACK = """
QWidget { background: #F8FAFC; color: #0F172A; font-size: 13px; }
QPushButton { min-height: 44px; min-width: 44px; padding: 8px 12px; border-radius: 6px; }
QPushButton#primary { background: #4F46E5; color: #fff; }
"""


def _read_dark_qss() -> str:
    if _DARK_QSS_PATH.is_file():
        return _DARK_QSS_PATH.read_text(encoding="utf-8")
    return _LIGHT_FALLBACK


def apply_theme(app, theme: str = "dark") -> None:  # type: ignore[no-untyped-def]
    if theme == "light":
        app.setStyleSheet(_LIGHT_FALLBACK)
        return
    app.setStyleSheet(_read_dark_qss())
