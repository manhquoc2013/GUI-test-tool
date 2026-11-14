"""Best-effort external Midscene/Playwright MCP via mcp_servers.json (no second Chromium)."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.binary_resolver import BinaryResolver
from ai_qa_test_studio.agent.mcp_client import McpClientFactory
from ai_qa_test_studio.agent.mcp_manager import McpManager
from ai_qa_test_studio.constants import DEFAULT_MCP_SERVERS_FILENAME

logger = logging.getLogger(__name__)

EXTERNAL_MIDSCENE = "midscene"
EXTERNAL_PLAYWRIGHT = "playwright"


class ExternalMcpBridge:
    """Starts optional npx MCP servers with in-memory CDP injection + JSON-RPC tools."""

    def __init__(
        self,
        config_path: Path | None = None,
        resolver: BinaryResolver | None = None,
        client: McpClientFactory | None = None,
    ) -> None:
        self.config_path = config_path or Path(DEFAULT_MCP_SERVERS_FILENAME)
        self.resolver = resolver or BinaryResolver()
        self._manager = McpManager(self.config_path, resolver=self.resolver)
        self._client = client or McpClientFactory(self._manager)

    @property
    def npx_available(self) -> bool:
        return self.resolver.which("npx") is not None

    @property
    def client(self) -> McpClientFactory:
        return self._client

    async def ensure_midscene(self, cdp_endpoint: str, log: Any = None) -> bool:
        return await self._ensure(EXTERNAL_MIDSCENE, cdp_endpoint, log=log)

    async def ensure_playwright(self, cdp_endpoint: str, log: Any = None) -> bool:
        return await self._ensure(EXTERNAL_PLAYWRIGHT, cdp_endpoint, log=log)

    async def call_tool(
        self,
        server: str,
        tool: str,
        arguments: dict[str, Any] | None = None,
        *,
        cdp_endpoint: str | None = None,
    ) -> dict[str, Any]:
        return await self._client.call_tool(
            server, tool, arguments, cdp_endpoint=cdp_endpoint
        )

    async def _ensure(self, name: str, cdp_endpoint: str, log: Any = None) -> bool:
        emit = log or (lambda _m: None)
        if self._client.get(name) is not None:
            return True
        if not self.npx_available:
            emit(f"External MCP {name}: npx không có — dùng bundled")
            return False
        if name not in self._manager.specs or self._manager.specs[name].disabled:
            emit(f"External MCP {name}: không cấu hình / disabled")
            return False
        try:
            await self._client.connect(name, cdp_endpoint=cdp_endpoint)
            emit(f"External MCP {name}: connected (JSON-RPC + CDP in-memory)")
            return True
        except Exception as exc:  # noqa: BLE001
            logger.warning("external_mcp_start_failed name=%s err=%s", name, exc)
            emit(f"External MCP {name}: lỗi khởi động ({exc}) — fallback bundled")
            return False

    async def stop_all(self) -> None:
        await self._client.close_all()
        await self._manager.stop_all()
