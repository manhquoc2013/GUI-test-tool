"""Dual-theme token tables and resolve helpers."""

from __future__ import annotations

from ai_qa_test_studio.ui.theme import (
    DARK_TOKENS,
    LIGHT_TOKENS,
    get_theme_tokens,
    surface_border_css,
    surface_card_css,
    surface_panel_css,
    text_primary_css,
)


def test_light_tokens_use_three_tier_surfaces() -> None:
    t = LIGHT_TOKENS
    assert t.bg_canvas == "#F3F4F6"
    assert t.bg_dock == "#F8FAFC"
    assert t.bg_card == "#FFFFFF"
    assert t.bg_topbar == "#FFFFFF"
    assert t.text_primary == "#0F172A"
    assert t.action_styles["click"] == ("rgba(2, 132, 199, 0.12)", "#0369A1")
    assert t.warning_fg == "#B45309"
    assert t.success_fg == "#047857"
    assert t.bubble_user_bg == "#EFF6FF"
    assert t.bubble_role_accents["planner"] == "#A16207"


def test_dark_tokens_match_design_elevation() -> None:
    t = DARK_TOKENS
    assert t.bg_canvas == "#18191C"
    assert t.bg_topbar == "#1E2025"
    assert t.text_primary == "#F8FAFC"
    assert t.action_styles["assert"][1] == "#FBBF24"
    assert "composer" in t.bubble_role_accents


def test_surface_helpers_honor_forced_theme() -> None:
    assert surface_panel_css(dark=False) == LIGHT_TOKENS.bg_dock
    assert surface_card_css(dark=False) == LIGHT_TOKENS.bg_card
    assert surface_border_css(dark=False) == LIGHT_TOKENS.border_hairline
    assert text_primary_css(dark=False) == LIGHT_TOKENS.text_primary
    assert surface_panel_css(dark=True) == DARK_TOKENS.bg_dock
    assert text_primary_css(dark=True) == DARK_TOKENS.text_primary


def test_get_theme_tokens_matches_is_dark_flag(monkeypatch: object) -> None:
    from ai_qa_test_studio.ui import theme as theme_mod

    monkeypatch.setattr(theme_mod, "isDarkTheme", lambda: True)
    assert get_theme_tokens() is DARK_TOKENS
    monkeypatch.setattr(theme_mod, "isDarkTheme", lambda: False)
    assert get_theme_tokens() is LIGHT_TOKENS
