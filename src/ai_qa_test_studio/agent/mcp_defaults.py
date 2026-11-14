"""Sync essential/opt-in MCP disabled flags into mcp_servers.json."""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import DEFAULT_MCP_SERVERS_FILENAME
from ai_qa_test_studio.core.models import McpExternalSettings

logger = logging.getLogger(__name__)


def sync_mcp_servers_disabled(
    external: McpExternalSettings,
    config_path: Path | None = None,
) -> None:
    path = config_path or Path(DEFAULT_MCP_SERVERS_FILENAME)
    if not path.is_file():
        return
    try:
        payload: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        logger.warning("mcp_servers_read_failed err=%s", exc)
        return
    servers = payload.get("mcpServers")
    if not isinstance(servers, dict):
        return
    desired = {
        "playwright": not external.playwright,
        "midscene": not external.midscene,
        "figma": not external.figma,
        "db": not external.db,
    }
    changed = False
    for name, disabled in desired.items():
        raw = servers.get(name)
        if not isinstance(raw, dict):
            continue
        if bool(raw.get("disabled", False)) != disabled:
            raw["disabled"] = disabled
            changed = True
    if not changed:
        return
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    logger.info("mcp_servers_synced path=%s", path)
