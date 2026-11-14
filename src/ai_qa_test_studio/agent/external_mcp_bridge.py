"""Best-effort external Midscene/Playwright MCP via mcp_servers.json (no second Chromium)."""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.binary_resolver import BinaryResolver
from ai_qa_test_studio.agent.mcp_manager import McpManager
from ai_qa_test_studio.constants import DEFAULT_MCP_SERVERS_FILENAME

logger = logging.getLogger(__name__)

EXTERNAL_MIDSCENE = "midscene"
EXTERNAL_PLAYWRIGHT = "playwright"


class ExternalMcpBridge:
    """Starts optional npx MCP servers with in-memory CDP injection."""

    def __init__(
        self,
        config_path: Path | None = None,
        resolver: BinaryResolver | None = None,
    ) -> None:
        self.config_path = config_path or Path(DEFAULT_MCP_SERVERS_FILENAME)
        self.resolver = resolver or BinaryResolver()
        self._manager = McpManager(self.config_path, resolver=self.resolver)
        self._procs: dict[str, asyncio.subprocess.Process] = {}

    @property
    def npx_available(self) -> bool:
        return self.resolver.which("npx") is not None

    async def ensure_midscene(self, cdp_endpoint: str, log: Any = None) -> bool:
        return await self._ensure(EXTERNAL_MIDSCENE, cdp_endpoint, log=log)

    async def ensure_playwright(self, cdp_endpoint: str, log: Any = None) -> bool:
        return await self._ensure(EXTERNAL_PLAYWRIGHT, cdp_endpoint, log=log)

    async def _ensure(self, name: str, cdp_endpoint: str, log: Any = None) -> bool:
        emit = log or (lambda _m: None)
        if name in self._procs and self._procs[name].returncode is None:
            return True
        if not self.npx_available:
            emit(f"External MCP {name}: npx không có — dùng bundled")
            return False
        if name not in self._manager.specs or self._manager.specs[name].disabled:
            emit(f"External MCP {name}: không cấu hình / disabled")
            return False
        try:
            proc = await self._manager.start_stdio(name, cdp_endpoint=cdp_endpoint)
            self._procs[name] = proc
            emit(f"External MCP {name}: started (CDP in-memory)")
            return True
        except Exception as exc:  # noqa: BLE001
            logger.warning("external_mcp_start_failed name=%s err=%s", name, exc)
            emit(f"External MCP {name}: lỗi khởi động ({exc}) — fallback bundled")
            return False

    async def stop_all(self) -> None:
        for name, proc in list(self._procs.items()):
            try:
                if proc.returncode is None:
                    proc.terminate()
                    try:
                        await asyncio.wait_for(proc.wait(), timeout=3.0)
                    except TimeoutError:
                        proc.kill()
            except Exception as exc:  # noqa: BLE001
                logger.warning("external_mcp_stop_failed name=%s err=%s", name, exc)
            self._procs.pop(name, None)
