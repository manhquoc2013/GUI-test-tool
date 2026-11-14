"""Bridge DualLoop healer vision_locate → external Midscene MCP, then bundled fallback."""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Any

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.external_mcp_bridge import EXTERNAL_MIDSCENE, ExternalMcpBridge
from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.constants import MCP_QA_MIDSCENE, MCP_TOOL_LOCATE, ROLE_SYSTEM

logger = logging.getLogger(__name__)

VisionLocateFn = Callable[[str], Awaitable[dict[str, Any] | None]]

_EXTERNAL_LOCATE_ALIASES = ("locate", "ai_locate", "midscene_locate", "locateElement")


async def make_vision_locate(
    page: Any,
    mcp: BundledMcpRegistry,
    enabled: bool,
    *,
    external: ExternalMcpBridge | None = None,
    cdp_endpoint: str = "",
) -> VisionLocateFn | None:
    if not enabled:
        return None

    async def locate(intent: str) -> dict[str, Any] | None:
        if external is not None and cdp_endpoint:
            external_hit = await _try_external_locate(external, cdp_endpoint, intent)
            if external_hit is not None:
                return external_hit
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
            return {**result, "source": "bundled"}
        return None

    return locate


async def _try_external_locate(
    bridge: ExternalMcpBridge,
    cdp_endpoint: str,
    intent: str,
) -> dict[str, Any] | None:
    try:
        ok = await bridge.ensure_midscene(cdp_endpoint)
        if not ok:
            return None
        session = bridge.client.get(EXTERNAL_MIDSCENE)
        if session is None:
            return None
        tool_names = await session.list_tool_names()
        tool = next((name for name in _EXTERNAL_LOCATE_ALIASES if name in tool_names), None)
        if tool is None and tool_names:
            tool = tool_names[0]
        if not tool:
            return None
        raw = await bridge.call_tool(
            EXTERNAL_MIDSCENE,
            tool,
            {"intent": intent, "prompt": intent, "description": intent},
            cdp_endpoint=cdp_endpoint,
        )
        if not raw.get("ok"):
            return None
        content = str(raw.get("content") or "")
        css = _extract_css_hint(content)
        if css:
            return {"css": css, "confidence": 0.7, "reason": "external_midscene", "source": "external"}
    except Exception as exc:  # noqa: BLE001
        logger.warning("external_midscene_locate_failed err=%s", exc)
    return None


def _extract_css_hint(content: str) -> str:
    text = (content or "").strip()
    if not text:
        return ""
    if text.startswith("#") or text.startswith(".") or text.startswith("["):
        return text.splitlines()[0].strip()
    for line in text.splitlines():
        stripped = line.strip()
        if "css" in stripped.lower() and ":" in stripped:
            return stripped.split(":", 1)[-1].strip().strip("\"'`")
    return ""
