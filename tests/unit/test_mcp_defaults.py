from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.agent.mcp_defaults import (
    resolve_mcp_servers_path,
    sync_mcp_servers_disabled,
)
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
    leftovers = list(tmp_path.glob(".mcp_servers.json.*.tmp"))
    assert leftovers == []


def test_resolve_mcp_servers_path_absolute(tmp_path: Path, monkeypatch) -> None:
    cfg = tmp_path / "nested" / "mcp_servers.json"
    cfg.parent.mkdir()
    cfg.write_text("{}", encoding="utf-8")
    resolved = resolve_mcp_servers_path(cfg)
    assert resolved.is_absolute()
    assert resolved == cfg.resolve()
    monkeypatch.setenv("AI_QA_MCP_SERVERS_PATH", str(cfg))
    assert resolve_mcp_servers_path() == cfg.resolve()


def test_sync_mcp_servers_noop_when_unchanged(tmp_path: Path) -> None:
    cfg = tmp_path / "mcp_servers.json"
    payload = {
        "mcpServers": {
            "playwright": {"command": "npx", "disabled": False},
            "midscene": {"command": "npx", "disabled": False},
            "figma": {"command": "npx", "disabled": True},
            "db": {"command": "npx", "disabled": True},
        }
    }
    cfg.write_text(json.dumps(payload), encoding="utf-8")
    before = cfg.stat().st_mtime_ns
    sync_mcp_servers_disabled(
        McpExternalSettings(playwright=True, midscene=True, figma=False, db=False),
        config_path=cfg,
    )
    assert cfg.stat().st_mtime_ns == before
