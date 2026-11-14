"""Single BrowserContext owner with ephemeral CDP on 127.0.0.1."""

from __future__ import annotations

import logging
import socket
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from ai_qa_test_studio.constants import CDP_HOST, GOTO_TIMEOUT_MS
from ai_qa_test_studio.core.auth_session import is_valid_storage_state

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
        self._storage_state: str | None = None

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

    @property
    def storage_state_path(self) -> str | None:
        return self._storage_state

    async def start(
        self,
        headless: bool = True,
        *,
        storage_state: str | Path | None = None,
    ) -> CdpEndpoint:
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
        context_kwargs: dict[str, Any] = {}
        state_path: str | None = None
        if storage_state is not None:
            path = Path(storage_state)
            if is_valid_storage_state(path):
                state_path = str(path)
                context_kwargs["storage_state"] = state_path
        self._storage_state = state_path
        self._context = await self._browser.new_context(**context_kwargs)
        self._page = await self._context.new_page()
        http = f"http://{CDP_HOST}:{port}"
        self._cdp = CdpEndpoint(
            browser_url=f"ws://{CDP_HOST}:{port}",
            http_endpoint=http,
            port=port,
        )
        logger.info(
            "browser_started cdp=%s storage_state=%s",
            self._cdp.http_endpoint,
            state_path or "",
        )
        return self._cdp

    async def goto(self, url: str, *, timeout_ms: int = GOTO_TIMEOUT_MS) -> None:
        await self.page.goto(url, wait_until="domcontentloaded", timeout=timeout_ms)

    async def save_storage_state(self, path: str | Path) -> Path:
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        await self.context.storage_state(path=str(target))
        return target

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
        self._storage_state = None

    def cdp_http_for_mcp(self) -> str:
        if self._cdp is None:
            raise RuntimeError("CDP not available")
        parsed = urlparse(self._cdp.http_endpoint)
        if parsed.hostname != CDP_HOST:
            raise RuntimeError("CDP must bind to 127.0.0.1")
        return self._cdp.http_endpoint
