from __future__ import annotations

from typing import Any

import pytest

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.vision_bridge import _extract_css_hint, make_vision_locate


def test_extract_css_hint_from_plain_selector() -> None:
    assert _extract_css_hint("#login-btn") == "#login-btn"
    assert _extract_css_hint('css: ".submit"') == ".submit"


@pytest.mark.asyncio
async def test_vision_locate_falls_back_to_bundled(monkeypatch: pytest.MonkeyPatch) -> None:
    class _Page:
        pass

    async def fake_extract(_self: Any, _page: Any) -> tuple[str, dict[str, Any]]:
        return (
            "compact",
            {"e1": {"name": "Sign in", "css": "#sign-in", "role": "button"}},
        )

    monkeypatch.setattr(
        "ai_qa_test_studio.agent.vision_bridge.DomDistiller.extract",
        fake_extract,
    )
    reg = BundledMcpRegistry()
    reg.start()
    locate = await make_vision_locate(_Page(), reg, enabled=True)
    assert locate is not None
    hit = await locate("sign in")
    assert hit is not None
    assert hit["css"] == "#sign-in"
    assert hit.get("source") == "bundled"
