from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from ai_qa_test_studio.agent.mcp_client import (
    ExternalMcpSession,
    McpClientFactory,
    serialize_tool_result,
)
from ai_qa_test_studio.agent.mcp_manager import StdioServerParameters


class _FakeContent:
    def __init__(self, text: str) -> None:
        self.type = "text"
        self.text = text


class _FakeToolResult:
    def __init__(self, text: str, is_error: bool = False) -> None:
        self.content = [_FakeContent(text)]
        self.isError = is_error


class _FakeSession:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict[str, Any] | None]] = []

    async def call_tool(self, name: str, arguments: dict[str, Any] | None = None) -> _FakeToolResult:
        self.calls.append((name, arguments))
        return _FakeToolResult(f"ok:{name}")

    async def list_tools(self) -> Any:
        class _Tools:
            tools = [type("T", (), {"name": "locate"})()]

        return _Tools()


def test_serialize_tool_result_text() -> None:
    payload = serialize_tool_result(_FakeToolResult("hello midscene"))
    assert payload["ok"] is True
    assert "hello midscene" in payload["content"]


@pytest.mark.asyncio
async def test_external_mcp_session_call_tool() -> None:
    fake = _FakeSession()
    session = ExternalMcpSession(name="midscene", session=fake, stack=None)
    tools = await session.list_tool_names()
    assert tools == ["locate"]
    result = await session.call_tool("locate", {"intent": "Login"})
    assert result["ok"] is True
    assert fake.calls == [("locate", {"intent": "Login"})]


@pytest.mark.asyncio
async def test_connect_reuses_session_until_cdp_changes() -> None:
    manager = MagicMock()
    manager.build_stdio_params.return_value = StdioServerParameters(
        command="echo", args=["ok"], env={}
    )
    factory = McpClientFactory(manager=manager)
    fake_session = _FakeSession()
    stack_closes: list[str] = []

    class _Stack:
        async def enter_async_context(self, _cm: Any) -> Any:
            if not hasattr(self, "_n"):
                self._n = 0
            self._n += 1
            if self._n == 1:
                return (object(), object())
            return fake_session

        async def aclose(self) -> None:
            stack_closes.append("closed")

    with (
        patch("ai_qa_test_studio.agent.mcp_client.AsyncExitStack", side_effect=_Stack),
        patch("ai_qa_test_studio.agent.mcp_client.stdio_client", return_value=object()),
        patch("ai_qa_test_studio.agent.mcp_client.ClientSession", return_value=fake_session),
    ):
        fake_session.initialize = AsyncMock()  # type: ignore[attr-defined]
        first = await factory.connect("playwright", cdp_endpoint="http://cdp-a")
        second = await factory.connect("playwright", cdp_endpoint="http://cdp-a")
        assert first is second
        assert stack_closes == []
        third = await factory.connect("playwright", cdp_endpoint="http://cdp-b")
        assert third is not first
        assert stack_closes == ["closed"]
        assert third.cdp_endpoint == "http://cdp-b"


@pytest.mark.asyncio
async def test_connect_aclose_stack_on_initialize_failure() -> None:
    manager = MagicMock()
    manager.build_stdio_params.return_value = StdioServerParameters(
        command="echo", args=["ok"], env={}
    )
    factory = McpClientFactory(manager=manager)
    closed = {"n": 0}

    class _FailSession:
        async def initialize(self) -> None:
            raise RuntimeError("spawn failed")

    class _Stack:
        async def enter_async_context(self, _cm: Any) -> Any:
            if not hasattr(self, "_n"):
                self._n = 0
            self._n += 1
            if self._n == 1:
                return (object(), object())
            return _FailSession()

        async def aclose(self) -> None:
            closed["n"] += 1

    with (
        patch("ai_qa_test_studio.agent.mcp_client.AsyncExitStack", side_effect=_Stack),
        patch("ai_qa_test_studio.agent.mcp_client.stdio_client", return_value=object()),
        patch("ai_qa_test_studio.agent.mcp_client.ClientSession", return_value=_FailSession()),
    ):
        with pytest.raises(RuntimeError, match="spawn failed"):
            await factory.connect("playwright", cdp_endpoint="http://cdp")
    assert closed["n"] == 1
    assert factory.get("playwright") is None
