from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.agent.external_mcp_bridge import ExternalMcpBridge


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
