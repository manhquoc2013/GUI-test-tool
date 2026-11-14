"""Bundled qa-midscene — zero-install intent→CSS locate via DOM element map."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from ai_qa_test_studio.constants import MCP_TOOL_LOCATE

_TOKEN_RE = re.compile(r"[a-z0-9]+", re.IGNORECASE)
_STOPWORDS = frozenset(
    {
        "a",
        "an",
        "the",
        "to",
        "for",
        "on",
        "in",
        "of",
        "and",
        "or",
        "click",
        "tap",
        "press",
        "type",
        "enter",
        "fill",
        "select",
        "open",
        "go",
        "with",
        "into",
        "from",
    }
)


@dataclass
class QaMidsceneServer:
    def locate(
        self,
        intent: str,
        element_map: dict[str, Any],
        compact_dom: str = "",
    ) -> dict[str, Any]:
        del compact_dom  # reserved for future DOM-text fallback
        tokens = [
            t.lower()
            for t in _TOKEN_RE.findall(intent or "")
            if len(t) > 1 and t.lower() not in _STOPWORDS
        ]
        if not tokens or not element_map:
            return {"css": "", "confidence": 0.0, "reason": "no_match"}

        best_id = ""
        best_css = ""
        best_score = 0.0
        for eid, meta in element_map.items():
            if not isinstance(meta, dict):
                continue
            haystack = " ".join(
                [
                    str(meta.get("name") or ""),
                    str(meta.get("css") or ""),
                    str(meta.get("role") or ""),
                    str(meta.get("placeholder") or ""),
                    str(meta.get("testId") or ""),
                    str(meta.get("tag") or ""),
                ]
            ).lower()
            hits = sum(1 for token in tokens if token in haystack)
            if hits == 0:
                continue
            score = hits / len(tokens)
            if score > best_score:
                best_score = score
                best_id = str(eid)
                best_css = str(meta.get("css") or "")

        if not best_css or best_score <= 0.0:
            return {"css": "", "confidence": 0.0, "reason": "no_match"}
        return {
            "css": best_css,
            "element_id": best_id,
            "confidence": round(best_score, 3),
            "reason": "dom_intent",
        }

    def health(self) -> dict[str, Any]:
        return {"ok": True, "server": "qa-midscene"}

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool == MCP_TOOL_LOCATE or tool == "locate":
            element_map = args.get("element_map") or {}
            if not isinstance(element_map, dict):
                element_map = {}
            return self.locate(
                str(args.get("intent") or ""),
                element_map,
                str(args.get("compact_dom") or ""),
            )
        if tool == "health":
            return self.health()
        raise KeyError(f"unknown tool: {tool}")
