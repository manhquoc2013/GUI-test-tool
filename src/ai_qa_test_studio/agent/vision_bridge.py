"""Bridge DualLoop healer vision_locate → external Midscene MCP, then bundled fallback."""

from __future__ import annotations

import json
import logging
import re
from collections.abc import Awaitable, Callable
from typing import Any

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.external_mcp_bridge import EXTERNAL_MIDSCENE, ExternalMcpBridge
from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.constants import MCP_QA_MIDSCENE, MCP_TOOL_LOCATE, ROLE_SYSTEM

logger = logging.getLogger(__name__)

VisionLocateFn = Callable[[str], Awaitable[dict[str, Any] | None]]

_EXTERNAL_LOCATE_ALIASES = (
    "ailocate",
    "ai_locate",
    "locate",
    "midscene_locate",
    "locateelement",
)
_CSS_SIMPLE_RE = re.compile(r"^([#.][\w-]+|\[[\w-]+(?:[*^$|]?=('[^']*'|\"[^\"]*\"|[^\]]+))?\])$")
_CSS_LABELED_RE = re.compile(
    r"(?:^|\b)(?:css|selector)\s*[:=]\s*([#.][\w-]+|\[[^\]]+\])",
    re.IGNORECASE,
)


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


def _pick_locate_tool(tool_names: list[str]) -> str | None:
    lowered = {name.lower(): name for name in tool_names if name}
    for alias in _EXTERNAL_LOCATE_ALIASES:
        if alias in lowered:
            return lowered[alias]
    return None


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
        tool = _pick_locate_tool(tool_names)
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


def _looks_like_css(candidate: str) -> bool:
    text = (candidate or "").strip()
    if not text or " " in text or len(text) > 200:
        return False
    if text.lower().startswith("error") or "not found" in text.lower():
        return False
    if text.startswith("{") or text.startswith("["):
        return False
    return bool(_CSS_SIMPLE_RE.match(text))


def _extract_css_hint(content: str) -> str:
    text = (content or "").strip()
    if not text:
        return ""
    if text.startswith("{") or text.startswith("["):
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            return ""
        if isinstance(payload, dict):
            for key in ("css", "selector", "locator"):
                value = str(payload.get(key) or "").strip()
                if _looks_like_css(value):
                    return value
        return ""
    if _looks_like_css(text.splitlines()[0].strip()):
        return text.splitlines()[0].strip()
    match = _CSS_LABELED_RE.search(text)
    if match:
        candidate = match.group(1).strip().strip("\"'`")
        if _looks_like_css(candidate):
            return candidate
    return ""
