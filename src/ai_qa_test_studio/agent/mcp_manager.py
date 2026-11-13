"""MCP Host with BinaryResolver; CDP endpoint injected in-memory only."""

from __future__ import annotations

import asyncio
import json
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.binary_resolver import BinaryResolver

logger = logging.getLogger(__name__)


@dataclass
class StdioServerParameters:
    command: str
    args: list[str] = field(default_factory=list)
    env: dict[str, str] | None = None


@dataclass
class McpServerSpec:
    name: str
    command: str
    args: list[str]
    cdp_endpoint_arg: str = "--cdp-endpoint"
    disabled: bool = False


class McpManager:
    def __init__(self, config_path: Path, resolver: BinaryResolver | None = None) -> None:
        self.config_path = config_path
        self.resolver = resolver or BinaryResolver()
        self.specs: dict[str, McpServerSpec] = {}
        self._processes: dict[str, asyncio.subprocess.Process] = {}
        self._load_config()

    def _load_config(self) -> None:
        if not self.config_path.is_file():
            return
        payload = json.loads(self.config_path.read_text(encoding="utf-8"))
        servers = payload.get("mcpServers") or {}
        for name, raw in servers.items():
            if not isinstance(raw, dict):
                continue
            self.specs[name] = McpServerSpec(
                name=name,
                command=str(raw.get("command") or "npx"),
                args=[str(a) for a in (raw.get("args") or [])],
                cdp_endpoint_arg=str(raw.get("cdpEndpointArg") or "--cdp-endpoint"),
                disabled=bool(raw.get("disabled", False)),
            )

    def build_stdio_params(
        self, name: str, cdp_endpoint: str | None = None
    ) -> StdioServerParameters:
        spec = self.specs[name]
        if spec.disabled:
            raise RuntimeError(f"MCP server disabled: {name}")
        command_path = self.resolver.require(spec.command)
        args = list(spec.args)
        if cdp_endpoint:
            # In-memory only — never persist ephemeral port to mcp_servers.json.
            args.extend([spec.cdp_endpoint_arg, cdp_endpoint])
        return StdioServerParameters(command=str(command_path), args=args)

    async def start_stdio(
        self, name: str, cdp_endpoint: str | None = None
    ) -> asyncio.subprocess.Process:
        params = self.build_stdio_params(name, cdp_endpoint=cdp_endpoint)
        proc = await asyncio.create_subprocess_exec(
            params.command,
            *params.args,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        self._processes[name] = proc
        logger.info("mcp_started name=%s pid=%s", name, proc.pid)
        return proc

    async def stop_all(self) -> None:
        for name, proc in list(self._processes.items()):
            if proc.returncode is None:
                proc.terminate()
                try:
                    await asyncio.wait_for(proc.wait(), timeout=5)
                except TimeoutError:
                    proc.kill()
            logger.info("mcp_stopped name=%s", name)
        self._processes.clear()

    def list_enabled(self) -> list[str]:
        return [n for n, s in self.specs.items() if not s.disabled]

    def health(self) -> dict[str, Any]:
        return {
            name: {
                "running": name in self._processes
                and self._processes[name].returncode is None,
                "disabled": spec.disabled,
            }
            for name, spec in self.specs.items()
        }
