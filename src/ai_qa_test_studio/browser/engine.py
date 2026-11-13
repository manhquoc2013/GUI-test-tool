"""Single BrowserContext owner with ephemeral CDP on 127.0.0.1."""

from __future__ import annotations

import logging
import socket
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse

from ai_qa_test_studio.constants import CDP_HOST, GOTO_TIMEOUT_MS

logger = logging.getLogger(__name__)


@dataclass
class CdpEndpoint:
    browser_url: str
    http_endpoint: str
    port: int


def _pick_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind((CDP_HOST, 0))
        return int(sock.getsockname()[1])


class BrowserEngine:
    def __init__(self) -> None:
        self._playwright: Any | None = None
        self._browser: Any | None = None
        self._context: Any | None = None
        self._page: Any | None = None
        self._cdp: CdpEndpoint | None = None

    @property
    def page(self) -> Any:
        if self._page is None:
            raise RuntimeError("Browser not started")
        return self._page

    @property
    def context(self) -> Any:
        if self._context is None:
            raise RuntimeError("Browser not started")
        return self._context

    @property
    def cdp_endpoint(self) -> CdpEndpoint | None:
        return self._cdp

    async def start(self, headless: bool = True) -> CdpEndpoint:
        """Launch Chromium with an ephemeral free port on 127.0.0.1 (not written to disk)."""
        from playwright.async_api import async_playwright

        port = _pick_free_port()
        self._playwright = await async_playwright().start()
        self._browser = await self._playwright.chromium.launch(
            headless=headless,
            args=[
                f"--remote-debugging-address={CDP_HOST}",
                f"--remote-debugging-port={port}",
            ],
        )
        self._context = await self._browser.new_context()
        self._page = await self._context.new_page()
        http = f"http://{CDP_HOST}:{port}"
        self._cdp = CdpEndpoint(
            browser_url=f"ws://{CDP_HOST}:{port}",
            http_endpoint=http,
            port=port,
        )
        logger.info("browser_started cdp=%s", self._cdp.http_endpoint)
        return self._cdp

    async def goto(self, url: str, *, timeout_ms: int = GOTO_TIMEOUT_MS) -> None:
        await self.page.goto(url, wait_until="domcontentloaded", timeout=timeout_ms)

    async def stop(self) -> None:
        if self._context is not None:
            await self._context.close()
        if self._browser is not None:
            await self._browser.close()
        if self._playwright is not None:
            await self._playwright.stop()
        self._page = None
        self._context = None
        self._browser = None
        self._playwright = None
        self._cdp = None

    def cdp_http_for_mcp(self) -> str:
        if self._cdp is None:
            raise RuntimeError("CDP not available")
        parsed = urlparse(self._cdp.http_endpoint)
        if parsed.hostname != CDP_HOST:
            raise RuntimeError("CDP must bind to 127.0.0.1")
        return self._cdp.http_endpoint
