from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_qa_test_studio.browser.snapshot_distiller import SnapshotDistiller
from ai_qa_test_studio.core.models import DistilledNode


@pytest.mark.asyncio
async def test_distill_returns_distilled_nodes_without_aria() -> None:
    page = MagicMock()
    page.evaluate = AsyncMock(
        return_value=[
            {
                "id": "e1",
                "siblingIndex": 0,
                "tag": "button",
                "role": "button",
                "name": "Sign in",
                "placeholder": "",
                "testId": "login",
                "css": "#submit",
                "xpath": "/html/body/button[1]",
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
    page.locator.assert_not_called()


@pytest.mark.asyncio
async def test_extract_legacy_builds_map() -> None:
    page = MagicMock()
    page.evaluate = AsyncMock(
        return_value=[
            {
                "id": "e1",
                "siblingIndex": 0,
                "tag": "input",
                "role": "",
                "name": "Email",
                "placeholder": "Email",
                "testId": "",
                "css": "#email",
                "xpath": "",
            }
        ]
    )
    distiller = SnapshotDistiller(include_aria=False)
    compact, element_map = await distiller.extract_legacy(page)
    assert "e1" in element_map
    assert element_map["e1"]["css"] == "#email"
    assert "[e1]" in compact
    assert "role=" not in compact or "input" in compact
