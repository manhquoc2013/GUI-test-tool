from __future__ import annotations

from unittest.mock import MagicMock

from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.core.models import ElementFingerprint


def test_chain_frame_locators_nested_and_nth() -> None:
    page = MagicMock()
    fl1 = MagicMock(name="fl1")
    fl2 = MagicMock(name="fl2")
    fl_nth = MagicMock(name="fl_nth")
    page.frame_locator.return_value = fl1
    fl1.frame_locator.return_value = fl2
    fl2.frame_locator.return_value = fl_nth
    fl_nth.nth.return_value = MagicMock(name="nth0")

    root = LocatorResolver.chain_frame_locators(
        page,
        ["iframe#outer", 'iframe[name="inner"]', "iframe >> nth=0"],
    )
    page.frame_locator.assert_called_with("iframe#outer")
    fl1.frame_locator.assert_called_with('iframe[name="inner"]')
    fl2.frame_locator.assert_called_with("iframe")
    fl_nth.nth.assert_called_with(0)
    assert root is fl_nth.nth.return_value


def test_frame_root_empty_returns_page() -> None:
    page = MagicMock()
    resolver = LocatorResolver()
    # sync helper via public chain
    assert LocatorResolver.chain_frame_locators(page, []) is page
    assert LocatorResolver.chain_frame_locators(page, None) is page
    _ = resolver
    fp = ElementFingerprint(frame_path=[])
    assert fp.frame_path == []
