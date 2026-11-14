"""Fluent dual-theme — ThemeTokens + live Dark/Light surface helpers (§2.2)."""

from __future__ import annotations

import sys
from dataclasses import dataclass

from PySide6.QtGui import QColor, QFont
from PySide6.QtWidgets import QApplication
from qfluentwidgets import Theme, isDarkTheme, setTheme, setThemeColor

from ai_qa_test_studio.constants import (
    THEME_ACCENT,
    THEME_CARD_RADIUS,
    THEME_PREF_AUTO,
    THEME_PREF_DARK,
    THEME_PREF_LIGHT,
)


@dataclass(frozen=True)
class ThemeTokens:
    bg_canvas: str
    bg_topbar: str
    bg_dock: str
    bg_card: str
    bg_card_subtle: str
    border_hairline: str
    border_card: str
    text_primary: str
    text_secondary: str
    text_muted: str
    warning_bg: str
    warning_border: str
    warning_fg: str
    warning_desc: str
    success_bg: str
    success_fg: str
    action_styles: dict[str, tuple[str, str]]
    # Copilot chat bubbles
    bubble_user_bg: str
    bubble_user_border: str
    bubble_user_fg: str
    bubble_agent_bg: str
    bubble_agent_border: str
    bubble_system_bg: str
    bubble_system_border: str
    bubble_role_accents: dict[str, str]


DARK_TOKENS = ThemeTokens(
    bg_canvas="#18191C",
    bg_topbar="#1E2025",
    bg_dock="rgba(255, 255, 255, 0.02)",
    bg_card="rgba(255, 255, 255, 0.03)",
    bg_card_subtle="rgba(255, 255, 255, 0.05)",
    border_hairline="rgba(255, 255, 255, 0.08)",
    border_card="rgba(255, 255, 255, 0.11)",
    text_primary="#F8FAFC",
    text_secondary="#94A3B8",
    text_muted="#64748B",
    warning_bg="rgba(245, 158, 11, 0.12)",
    warning_border="rgba(245, 158, 11, 0.32)",
    warning_fg="#FBBF24",
    warning_desc="#FDE68A",
    success_bg="rgba(16, 185, 129, 0.14)",
    success_fg="#34D399",
    action_styles={
        "click": ("rgba(56, 189, 248, 0.16)", "#38BDF8"),
        "fill": ("rgba(52, 211, 153, 0.16)", "#34D399"),
        "select": ("rgba(167, 139, 250, 0.16)", "#A78BFA"),
        "assert": ("rgba(251, 191, 36, 0.16)", "#FBBF24"),
    },
    bubble_user_bg="rgba(14, 165, 233, 0.18)",
    bubble_user_border="rgba(56, 189, 248, 0.45)",
    bubble_user_fg="#E0F2FE",
    bubble_agent_bg="rgba(255, 255, 255, 0.05)",
    bubble_agent_border="rgba(255, 255, 255, 0.12)",
    bubble_system_bg="rgba(245, 158, 11, 0.10)",
    bubble_system_border="rgba(245, 158, 11, 0.28)",
    bubble_role_accents={
        "user": "#38BDF8",
        "planner": "#FACC15",
        "composer": "#A5B4FC",
        "critic": "#FB7185",
        "analyzer": "#34D399",
        "optimizer": "#67E8F9",
        "crew": "#94A3B8",
        "system": "#FBBF24",
        "assistant": "#A5B4FC",
    },
)

LIGHT_TOKENS = ThemeTokens(
    bg_canvas="#F3F4F6",
    bg_topbar="#FFFFFF",
    bg_dock="#F8FAFC",
    bg_card="#FFFFFF",
    bg_card_subtle="#F1F5F9",
    border_hairline="rgba(15, 23, 42, 0.09)",
    border_card="rgba(15, 23, 42, 0.12)",
    text_primary="#0F172A",
    text_secondary="#475569",
    text_muted="#64748B",
    warning_bg="rgba(245, 158, 11, 0.11)",
    warning_border="rgba(217, 119, 6, 0.35)",
    warning_fg="#B45309",
    warning_desc="#78350F",
    success_bg="rgba(16, 185, 129, 0.13)",
    success_fg="#047857",
    action_styles={
        "click": ("rgba(2, 132, 199, 0.12)", "#0369A1"),
        "fill": ("rgba(5, 150, 105, 0.12)", "#047857"),
        "select": ("rgba(124, 58, 237, 0.12)", "#6D28D9"),
        "assert": ("rgba(217, 119, 6, 0.13)", "#B45309"),
    },
    bubble_user_bg="#EFF6FF",
    bubble_user_border="#BFDBFE",
    bubble_user_fg="#1E3A8A",
    bubble_agent_bg="#F8FAFC",
    bubble_agent_border="#E2E8F0",
    bubble_system_bg="#FFFBEB",
    bubble_system_border="#FCD34D",
    bubble_role_accents={
        "user": "#0369A1",
        "planner": "#A16207",
        "composer": "#4F46E5",
        "critic": "#BE123C",
        "analyzer": "#047857",
        "optimizer": "#0E7490",
        "crew": "#64748B",
        "system": "#B45309",
        "assistant": "#4F46E5",
    },
)


def get_theme_tokens(*, dark: bool | None = None) -> ThemeTokens:
    use_dark = isDarkTheme() if dark is None else dark
    return DARK_TOKENS if use_dark else LIGHT_TOKENS


def elevation_channel(*, dark: bool | None = None) -> int:
    use_dark = isDarkTheme() if dark is None else dark
    return 255 if use_dark else 0


def surface_panel_css(*, dark: bool | None = None) -> str:
    return get_theme_tokens(dark=dark).bg_dock


def surface_card_css(*, dark: bool | None = None) -> str:
    return get_theme_tokens(dark=dark).bg_card


def surface_border_css(*, dark: bool | None = None) -> str:
    return get_theme_tokens(dark=dark).border_hairline


def unbound_banner_bg_css(*, dark: bool | None = None) -> str:
    return get_theme_tokens(dark=dark).warning_bg


def unbound_banner_border_css(*, dark: bool | None = None) -> str:
    return get_theme_tokens(dark=dark).warning_border


def text_primary_css(*, dark: bool | None = None) -> str:
    return get_theme_tokens(dark=dark).text_primary


def action_badge_colors(action: str, *, dark: bool | None = None) -> tuple[str, str]:
    t = get_theme_tokens(dark=dark)
    return t.action_styles.get(action.lower(), (t.bg_card_subtle, t.text_secondary))


def action_badge_bg_css(fg_hex: str) -> str:
    """Legacy helper: translucent bg from an #RRGGBB foreground (prefer action_badge_colors)."""
    hex_color = fg_hex.lstrip("#")
    if len(hex_color) != 6:
        return get_theme_tokens().bg_card_subtle
    r = int(hex_color[0:2], 16)
    g = int(hex_color[2:4], 16)
    b = int(hex_color[4:6], 16)
    return f"rgba({r}, {g}, {b}, 0.15)"


def panel_stylesheet(object_name: str, *, border_left: bool = False) -> str:
    t = get_theme_tokens()
    border = (
        f"border-left: 1px solid {t.border_hairline};"
        if border_left
        else f"border: 1px solid {t.border_hairline};"
    )
    return f"#{object_name} {{ background-color: {t.bg_dock}; {border} }}"


def card_stylesheet(object_name: str) -> str:
    t = get_theme_tokens()
    return (
        f"{object_name} {{"
        f"background-color: {t.bg_card};"
        f"border: 1px solid {t.border_card};"
        f"border-radius: 10px;"
        f"}}"
    )


def studio_shell_qss(*, dark: bool | None = None) -> str:
    t = get_theme_tokens(dark=dark)
    return f"""
QMainWindow {{
    background-color: {t.bg_canvas};
    color: {t.text_primary};
}}
CardWidget {{
    background-color: {t.bg_card};
    border: none;
    border-radius: {THEME_CARD_RADIUS}px;
}}
QSplitter::handle {{
    background-color: {t.border_hairline};
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
    background-color: {t.bg_topbar};
    border-top: 1px solid {t.border_hairline};
    color: {t.text_muted};
    padding: 2px 8px;
    min-height: 28px;
    max-height: 28px;
}}
QStatusBar::item {{
    border: none;
}}
QScrollArea, QWidget#qt_scrollarea_viewport {{
    background-color: transparent;
    border: none;
}}
"""


# Backward-compatible name used by older imports/tests.
FLUENT_DARK_QSS = studio_shell_qss(dark=True)


def _platform_ui_font() -> QFont:
    if sys.platform == "darwin":
        return QFont(".AppleSystemUIFont", 13)
    if sys.platform.startswith("win"):
        return QFont("Segoe UI", 13)
    return QFont("Sans Serif", 13)


def resolve_fluent_theme(preference: str) -> Theme:
    pref = (preference or THEME_PREF_AUTO).strip().lower()
    if pref == THEME_PREF_LIGHT:
        return Theme.LIGHT
    if pref == THEME_PREF_DARK:
        return Theme.DARK
    return Theme.AUTO


def apply_studio_theme(app: QApplication, preference: str = THEME_PREF_AUTO) -> None:
    setTheme(resolve_fluent_theme(preference))
    setThemeColor(QColor(THEME_ACCENT))
    app.setFont(_platform_ui_font())
    app.setStyleSheet(studio_shell_qss())


def apply_theme(app: QApplication, theme: str = THEME_PREF_AUTO) -> None:
    """Backward-compatible entry used by ``__main__``."""
    apply_studio_theme(app, preference=theme)


def toggle_resolved_theme() -> Theme:
    """Flip current resolved Dark/Light (used by TopBar when preference becomes explicit)."""
    next_theme = Theme.LIGHT if isDarkTheme() else Theme.DARK
    setTheme(next_theme)
    return next_theme
