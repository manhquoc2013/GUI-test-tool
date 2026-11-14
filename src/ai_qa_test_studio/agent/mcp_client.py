"""JSON-RPC MCP stdio client sessions (external servers)."""

from __future__ import annotations

import logging
from contextlib import AsyncExitStack
from dataclasses import dataclass, field
from typing import Any

from mcp import ClientSession
from mcp.client.stdio import StdioServerParameters as McpStdioParams
from mcp.client.stdio import stdio_client

from ai_qa_test_studio.agent.mcp_manager import McpManager, StdioServerParameters

logger = logging.getLogger(__name__)


def serialize_tool_result(result: Any) -> dict[str, Any]:
    is_error = bool(getattr(result, "isError", False) or getattr(result, "is_error", False))
    chunks: list[str] = []
    content = getattr(result, "content", None) or []
    for item in content:
        text = getattr(item, "text", None)
        if text is not None:
            chunks.append(str(text))
        else:
            chunks.append(str(item))
    body = "\n".join(chunks).strip()
    return {"ok": not is_error, "content": body, "is_error": is_error}


@dataclass
class ExternalMcpSession:
    name: str
    session: Any
    stack: AsyncExitStack | None = None
    cdp_endpoint: str | None = None

    async def list_tool_names(self) -> list[str]:
        listed = await self.session.list_tools()
        tools = getattr(listed, "tools", None) or []
        return [str(getattr(tool, "name", "")) for tool in tools if getattr(tool, "name", "")]

    async def call_tool(self, tool: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        result = await self.session.call_tool(tool, arguments or {})
        return serialize_tool_result(result)

    async def close(self) -> None:
        if self.stack is not None:
            await self.stack.aclose()
            self.stack = None


@dataclass
class McpClientFactory:
    """Opens long-lived stdio MCP sessions via the official SDK."""

    manager: McpManager
    _sessions: dict[str, ExternalMcpSession] = field(default_factory=dict)

    def get(self, name: str) -> ExternalMcpSession | None:
        return self._sessions.get(name)

    async def connect(self, name: str, cdp_endpoint: str | None = None) -> ExternalMcpSession:
        existing = self._sessions.get(name)
        if existing is not None:
            if (existing.cdp_endpoint or "") == (cdp_endpoint or ""):
                return existing
            await existing.close()
            self._sessions.pop(name, None)
            logger.info("mcp_client_reconnect name=%s reason=cdp_changed", name)

        params = self.manager.build_stdio_params(name, cdp_endpoint=cdp_endpoint)
        mcp_params = self._to_mcp_params(params)
        stack = AsyncExitStack()
        try:
            read, write = await stack.enter_async_context(stdio_client(mcp_params))
            session = await stack.enter_async_context(ClientSession(read, write))
            await session.initialize()
        except Exception:
            try:
                await stack.aclose()
            except Exception as close_exc:  # noqa: BLE001
                logger.warning("mcp_client_stack_close_failed name=%s err=%s", name, close_exc)
            raise
        wrapped = ExternalMcpSession(
            name=name, session=session, stack=stack, cdp_endpoint=cdp_endpoint
        )
        self._sessions[name] = wrapped
        logger.info("mcp_client_connected name=%s", name)
        return wrapped

    async def call_tool(
        self,
        server: str,
        tool: str,
        arguments: dict[str, Any] | None = None,
        *,
        cdp_endpoint: str | None = None,
    ) -> dict[str, Any]:
        session = await self.connect(server, cdp_endpoint=cdp_endpoint)
        return await session.call_tool(tool, arguments)

    async def close_all(self) -> None:
        for name, session in list(self._sessions.items()):
            try:
                await session.close()
            except Exception as exc:  # noqa: BLE001
                logger.warning("mcp_client_close_failed name=%s err=%s", name, exc)
            self._sessions.pop(name, None)

    @staticmethod
    def _to_mcp_params(params: StdioServerParameters) -> McpStdioParams:
        return McpStdioParams(
            command=params.command,
            args=list(params.args),
            env=params.env,
        )
