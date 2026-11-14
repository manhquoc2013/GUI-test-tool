from __future__ import annotations

from typing import Any

import pytest

from ai_qa_test_studio.agent.mcp_client import (
    ExternalMcpSession,
    serialize_tool_result,
)


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
