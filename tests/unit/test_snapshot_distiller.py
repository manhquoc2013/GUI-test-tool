from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_qa_test_studio.browser.snapshot_distiller import SnapshotDistiller
from ai_qa_test_studio.constants import ATTR_IN_SHADOW_DOM, UI_VIEWPORT_HEIGHT, UI_VIEWPORT_WIDTH
from ai_qa_test_studio.core.models import DistilledNode


@pytest.mark.asyncio
async def test_distill_returns_distilled_nodes_without_aria() -> None:
    page = MagicMock()
    page.main_frame = MagicMock(child_frames=[])
    page.viewport_size = {"width": UI_VIEWPORT_WIDTH, "height": UI_VIEWPORT_HEIGHT}
    page.evaluate = AsyncMock(
        return_value=[
            {
                "tag": "button",
                "role": "button",
                "name": "Sign in",
                "placeholder": "",
                "testId": "login",
                "css": "#submit",
                "xpath": "/html/body/button[1]",
                "inShadowDom": False,
                "shadowHostChain": [],
                "bbox": {"x": 10, "y": 20, "w": 100, "h": 40},
            }
        ]
    )
    page.locator = MagicMock()
    distiller = SnapshotDistiller(include_aria=False)
    nodes = await distiller.distill(page)
    assert len(nodes) == 1
    assert isinstance(nodes[0], DistilledNode)
    assert nodes[0].short_id == "e1"
    assert nodes[0].fingerprint.test_id == "login"
    assert nodes[0].fingerprint.frame_path == []
    assert "short_id" not in nodes[0].fingerprint.model_dump()
    assert nodes[0].bbox_norm is not None
    page.locator.assert_not_called()


@pytest.mark.asyncio
async def test_extract_legacy_builds_map() -> None:
    page = MagicMock()
    page.main_frame = MagicMock(child_frames=[])
    page.evaluate = AsyncMock(
        return_value=[
            {
                "tag": "input",
                "role": "",
                "name": "Email",
                "placeholder": "Email",
                "testId": "",
                "css": "#email",
                "xpath": "",
                "inShadowDom": False,
                "shadowHostChain": [],
            }
        ]
    )
    distiller = SnapshotDistiller(include_aria=False)
    compact, element_map = await distiller.extract_legacy(page)
    assert "e1" in element_map
    assert element_map["e1"]["css"] == "#email"
    assert "[e1]" in compact
    assert "role=" not in compact or "input" in compact


@pytest.mark.asyncio
async def test_distill_shadow_and_iframe_offset() -> None:
    page = MagicMock()
    page.viewport_size = {"width": 1000, "height": 1000}
    page.evaluate = AsyncMock(
        return_value=[
            {
                "tag": "button",
                "role": "button",
                "name": "Hosted",
                "placeholder": "",
                "testId": "shadow-btn",
                "css": "my-widget >> #go",
                "xpath": "/button[1]",
                "inShadowDom": True,
                "shadowHostChain": ["my-widget"],
                "bbox": {"x": 0, "y": 0, "w": 10, "h": 10},
            }
        ]
    )
    child = MagicMock()
    child.child_frames = []
    child.evaluate = AsyncMock(
        return_value=[
            {
                "tag": "input",
                "role": "",
                "name": "Card",
                "placeholder": "",
                "testId": "card",
                "css": "#card",
                "xpath": "",
                "inShadowDom": False,
                "shadowHostChain": [],
                "bbox": {"x": 5, "y": 5, "w": 20, "h": 20},
            }
        ]
    )
    frame_el = MagicMock()
    frame_el.evaluate = AsyncMock(
        return_value={
            "hidden": False,
            "tracking": False,
            "sel": "iframe#pay",
            "x": 100.0,
            "y": 50.0,
        }
    )
    child.frame_element = AsyncMock(return_value=frame_el)
    page.main_frame = MagicMock(child_frames=[child])

    nodes = await SnapshotDistiller(include_aria=False).distill(page)
    assert len(nodes) == 2
    shadow = nodes[0]
    assert shadow.fingerprint.attrs_subset.get(ATTR_IN_SHADOW_DOM) == "true"
    assert ">>" in (shadow.fingerprint.css or "")
    framed = nodes[1]
    assert framed.fingerprint.frame_path == ["iframe#pay"]
    assert framed.fingerprint.test_id == "card"
    assert framed.bbox_norm is not None
    assert framed.bbox_norm[0] == pytest.approx(0.105)
    assert framed.bbox_norm[1] == pytest.approx(0.055)
