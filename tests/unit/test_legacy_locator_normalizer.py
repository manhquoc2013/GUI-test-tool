from __future__ import annotations

from ai_qa_test_studio.core.legacy_locator_normalizer import (
    normalize_action,
    normalize_locator,
    normalize_variables,
)


def test_normalize_id_prefix() -> None:
    assert normalize_locator("id=loginBtn") == "#loginBtn"


def test_normalize_css_and_xpath_prefixes() -> None:
    assert normalize_locator("css=.submit") == ".submit"
    assert normalize_locator("xpath=//button[@type='submit']") == "xpath=//button[@type='submit']"


def test_normalize_by_id() -> None:
    assert normalize_locator('By.id("email")') == "#email"


def test_normalize_variables() -> None:
    assert normalize_variables("user ${username} / ${password}") == "user {{username}} / {{password}}"


def test_normalize_action() -> None:
    assert normalize_action("NAVIGATE") == "goto"
    assert normalize_action("FILL") == "fill"
