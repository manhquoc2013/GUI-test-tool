from __future__ import annotations

from typing import Any

import pytest

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.vision_bridge import (
    _extract_css_hint,
    _pick_locate_tool,
    make_vision_locate,
)


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("#login-btn", "#login-btn"),
        (".submit", ".submit"),
        ('{"css":"#ok"}', "#ok"),
        ('{"selector":"#x"}', "#x"),
        ('[{"center":[10,20]}]', ""),
        ("Error: css selector not found", ""),
        ("css: #ok", "#ok"),
        ("", ""),
    ],
)
def test_extract_css_hint_rejects_garbage(raw: str, expected: str) -> None:
    assert _extract_css_hint(raw) == expected


def test_pick_locate_tool_prefers_ailocate_never_first_action() -> None:
    assert _pick_locate_tool(["aiTap", "aiAction", "aiLocate"]) == "aiLocate"
    assert _pick_locate_tool(["aiTap", "aiAction"]) is None
    assert _pick_locate_tool(["locate", "aiTap"]) == "locate"


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


@pytest.mark.asyncio
async def test_external_garbage_css_falls_through_to_bundled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class _Page:
        pass

    class _Bridge:
        async def ensure_midscene(self, _cdp: str, log: Any = None) -> bool:
            del log
            return True

        @property
        def client(self) -> Any:
            return self

        def get(self, _name: str) -> Any:
            class _S:
                async def list_tool_names(self) -> list[str]:
                    return ["aiLocate"]

            return _S()

        async def call_tool(self, *_a: Any, **_k: Any) -> dict[str, Any]:
            return {"ok": True, "content": '[{"center":[1,2]}]'}

    async def fake_extract(_self: Any, _page: Any) -> tuple[str, dict[str, Any]]:
        return ("compact", {"e1": {"name": "Submit", "css": "#submit", "role": "button"}})

    monkeypatch.setattr(
        "ai_qa_test_studio.agent.vision_bridge.DomDistiller.extract",
        fake_extract,
    )
    reg = BundledMcpRegistry()
    reg.start()
    locate = await make_vision_locate(
        _Page(), reg, enabled=True, external=_Bridge(), cdp_endpoint="http://127.0.0.1:9222"
    )
    assert locate is not None
    hit = await locate("submit")
    assert hit is not None
    assert hit["css"] == "#submit"
    assert hit.get("source") == "bundled"
