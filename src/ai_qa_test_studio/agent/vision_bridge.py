"""Bridge DualLoop healer vision_locate → bundled qa-midscene (zero-install)."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.constants import MCP_QA_MIDSCENE, MCP_TOOL_LOCATE, ROLE_SYSTEM

VisionLocateFn = Callable[[str], Awaitable[dict[str, Any] | None]]


async def make_vision_locate(
    page: Any,
    mcp: BundledMcpRegistry,
    enabled: bool,
) -> VisionLocateFn | None:
    if not enabled:
        return None

    async def locate(intent: str) -> dict[str, Any] | None:
        distiller = DomDistiller()
        compact, element_map = await distiller.extract(page)
        result = mcp.invoke(
            MCP_QA_MIDSCENE,
            MCP_TOOL_LOCATE,
            {
                "intent": intent,
                "element_map": element_map,
                "compact_dom": compact,
            },
            role=ROLE_SYSTEM,
        )
        if result.get("css"):
            return result
        return None

    return locate
