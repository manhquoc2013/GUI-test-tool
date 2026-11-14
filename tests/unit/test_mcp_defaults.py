from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.agent.mcp_defaults import sync_mcp_servers_disabled
from ai_qa_test_studio.core.models import McpExternalSettings


def test_sync_mcp_servers_disabled_flags(tmp_path: Path) -> None:
    cfg = tmp_path / "mcp_servers.json"
    cfg.write_text(
        json.dumps(
            {
                "mcpServers": {
                    "playwright": {"command": "npx", "disabled": True},
                    "midscene": {"command": "npx", "disabled": True},
                    "figma": {"command": "npx", "disabled": False},
                    "db": {"command": "npx", "disabled": False},
                }
            }
        ),
        encoding="utf-8",
    )
    sync_mcp_servers_disabled(
        McpExternalSettings(playwright=True, midscene=True, figma=False, db=False),
        config_path=cfg,
    )
    payload = json.loads(cfg.read_text(encoding="utf-8"))
    assert payload["mcpServers"]["playwright"]["disabled"] is False
    assert payload["mcpServers"]["midscene"]["disabled"] is False
    assert payload["mcpServers"]["figma"]["disabled"] is True
    assert payload["mcpServers"]["db"]["disabled"] is True
