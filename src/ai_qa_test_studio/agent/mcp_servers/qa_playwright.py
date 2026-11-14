"""Bundled qa-playwright — screenshot/cdp_info against the bound page only."""

from __future__ import annotations

import base64
import inspect
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any


@dataclass
class QaPlaywrightServer:
    page_provider: Callable[[], Any] | None = None
    _cdp_endpoint: str = field(default="", repr=False)

    def bind_page(self, page_provider: Callable[[], Any] | None) -> None:
        self.page_provider = page_provider

    def bind_cdp(self, endpoint: str) -> None:
        self._cdp_endpoint = (endpoint or "").strip()

    def _resolve_page(self) -> Any | None:
        if self.page_provider is None:
            return None
        try:
            return self.page_provider()
        except Exception:  # noqa: BLE001
            return None

    def screenshot(self) -> dict[str, Any]:
        page = self._resolve_page()
        if page is None:
            return {"available": False, "png_b64": "", "width": 0, "height": 0}
        shot = getattr(page, "screenshot", None)
        if shot is None or not callable(shot):
            return {"available": False, "png_b64": "", "width": 0, "height": 0}
        try:
            raw = shot(type="png", full_page=False)
        except TypeError:
            try:
                raw = shot()
            except Exception:  # noqa: BLE001
                return {"available": False, "png_b64": "", "width": 0, "height": 0}
        except Exception:  # noqa: BLE001
            return {"available": False, "png_b64": "", "width": 0, "height": 0}
        if inspect.isawaitable(raw):
            # Sync MCP surface — callers that need live async shots use SessionController.
            return {
                "available": False,
                "png_b64": "",
                "width": 0,
                "height": 0,
                "reason": "async_page",
            }
        if not isinstance(raw, (bytes, bytearray)):
            return {"available": False, "png_b64": "", "width": 0, "height": 0}
        viewport = getattr(page, "viewport_size", None) or {}
        width = int(viewport.get("width") or 0) if isinstance(viewport, dict) else 0
        height = int(viewport.get("height") or 0) if isinstance(viewport, dict) else 0
        return {
            "available": True,
            "png_b64": base64.b64encode(bytes(raw)).decode("ascii"),
            "width": width,
            "height": height,
        }

    def cdp_info(self) -> dict[str, Any]:
        endpoint = self._cdp_endpoint
        return {
            "available": bool(endpoint),
            "endpoint": endpoint,
        }

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        del args
        if tool == "screenshot":
            return self.screenshot()
        if tool == "cdp_info":
            return self.cdp_info()
        if tool == "health":
            return {"ok": True, "server": "qa-playwright", "cdp": bool(self._cdp_endpoint)}
        raise KeyError(f"unknown tool: {tool}")
