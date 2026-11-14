"""Sync essential/opt-in MCP disabled flags into mcp_servers.json."""

from __future__ import annotations

import json
import logging
import os
import tempfile
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import DEFAULT_MCP_SERVERS_FILENAME
from ai_qa_test_studio.core.models import McpExternalSettings

logger = logging.getLogger(__name__)


def resolve_mcp_servers_path(config_path: Path | None = None) -> Path:
    if config_path is not None:
        return config_path.resolve()
    env_path = os.environ.get("AI_QA_MCP_SERVERS_PATH", "").strip()
    if env_path:
        return Path(env_path).expanduser().resolve()
    return (Path.cwd() / DEFAULT_MCP_SERVERS_FILENAME).resolve()


def sync_mcp_servers_disabled(
    external: McpExternalSettings,
    config_path: Path | None = None,
) -> None:
    path = resolve_mcp_servers_path(config_path)
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
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    fd, tmp_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent)
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)
    except Exception:
        try:
            os.unlink(tmp_name)
        except OSError:
            pass
        raise
    logger.info("mcp_servers_synced path=%s", path)
