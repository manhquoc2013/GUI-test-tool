from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest

from ai_qa_test_studio.agent.external_mcp_bridge import ExternalMcpBridge
from ai_qa_test_studio.agent.mcp_client import ExternalMcpSession, McpClientFactory


class _FakeClient(McpClientFactory):
    def __init__(self) -> None:
        self.connected: list[str] = []
        self._sessions: dict[str, ExternalMcpSession] = {}

    async def connect(self, name: str, cdp_endpoint: str | None = None) -> ExternalMcpSession:
        del cdp_endpoint
        self.connected.append(name)

        class _Sess:
            async def call_tool(self, tool: str, arguments: dict[str, Any] | None = None) -> Any:
                return type(
                    "R",
                    (),
                    {
                        "content": [type("C", (), {"text": f"{tool}:{arguments}"})()],
                        "isError": False,
                    },
                )()

            async def list_tools(self) -> Any:
                return type("L", (), {"tools": []})()

        session = ExternalMcpSession(name=name, session=_Sess(), stack=None)
        self._sessions[name] = session
        return session

    async def close_all(self) -> None:
        self._sessions.clear()


@pytest.mark.asyncio
async def test_ensure_midscene_without_npx_returns_false(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    cfg = tmp_path / "mcp_servers.json"
    cfg.write_text(
        '{"mcpServers":{"midscene":{"command":"npx","args":["-y","@midscene/mcp"]}}}',
        encoding="utf-8",
    )
    bridge = ExternalMcpBridge(config_path=cfg)
    monkeypatch.setattr(bridge.resolver, "which", lambda _n: None)
    assert bridge.npx_available is False
    ok = await bridge.ensure_midscene("http://127.0.0.1:9222")
    assert ok is False


@pytest.mark.asyncio
async def test_ensure_midscene_connects_via_client(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    cfg = tmp_path / "mcp_servers.json"
    cfg.write_text(
        '{"mcpServers":{"midscene":{"command":"npx","args":["-y","@midscene/mcp"]}}}',
        encoding="utf-8",
    )
    fake = _FakeClient()
    bridge = ExternalMcpBridge(config_path=cfg, client=fake)
    monkeypatch.setattr(bridge.resolver, "which", lambda _n: Path("/usr/bin/npx"))
    ok = await bridge.ensure_midscene("http://127.0.0.1:9222")
    assert ok is True
    assert fake.connected == ["midscene"]
    result = await bridge.call_tool("midscene", "locate", {"intent": "Submit"})
    assert result["ok"] is True
    assert "locate" in result["content"]
