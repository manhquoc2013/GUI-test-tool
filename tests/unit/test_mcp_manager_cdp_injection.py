from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.agent.binary_resolver import BinaryResolver
from ai_qa_test_studio.agent.mcp_manager import McpManager


def test_cdp_injected_in_memory_without_writing_config(tmp_path: Path) -> None:
    config = tmp_path / "mcp_servers.json"
    config.write_text(
        json.dumps(
            {
                "mcpServers": {
                    "playwright": {
                        "command": "python3",
                        "args": ["-c", "pass"],
                        "cdpEndpointArg": "--cdp-endpoint",
                    }
                }
            }
        ),
        encoding="utf-8",
    )
    original = config.read_text(encoding="utf-8")
    manager = McpManager(config, resolver=_FakeResolver())
    params = manager.build_stdio_params("playwright", cdp_endpoint="http://127.0.0.1:9333")
    assert "--cdp-endpoint" in params.args
    assert "http://127.0.0.1:9333" in params.args
    assert config.read_text(encoding="utf-8") == original


class _FakeResolver(BinaryResolver):
    def require(self, name: str) -> Path:
        return Path("/usr/bin/python3")
