"""In-process MCP registry — auto-ready on app start, no user install."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.agent.mcp_servers.qa_compress import QaCompressServer
from ai_qa_test_studio.agent.mcp_servers.qa_crew_memory import QaCrewMemoryServer
from ai_qa_test_studio.agent.mcp_servers.qa_project_context import QaProjectContextServer
from ai_qa_test_studio.constants import (
    MCP_QA_COMPRESS,
    MCP_QA_CREW_MEMORY,
    MCP_QA_PROJECT_CONTEXT,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_PLANNER,
)
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)

_ROLE_TOOLS: dict[str, frozenset[str]] = {
    ROLE_PLANNER: frozenset(
        {
            f"{MCP_QA_COMPRESS}.compress",
            f"{MCP_QA_COMPRESS}.stats",
            f"{MCP_QA_CREW_MEMORY}.put_artifact",
            f"{MCP_QA_CREW_MEMORY}.get_summary",
            f"{MCP_QA_CREW_MEMORY}.get_full",
            f"{MCP_QA_PROJECT_CONTEXT}.case_brief",
            f"{MCP_QA_PROJECT_CONTEXT}.srs_excerpt",
        }
    ),
    ROLE_COMPOSER: frozenset(
        {
            f"{MCP_QA_COMPRESS}.compress",
            f"{MCP_QA_COMPRESS}.retrieve",
            f"{MCP_QA_CREW_MEMORY}.put_artifact",
            f"{MCP_QA_CREW_MEMORY}.get_summary",
            f"{MCP_QA_CREW_MEMORY}.get_full",
            f"{MCP_QA_PROJECT_CONTEXT}.case_brief",
            f"{MCP_QA_PROJECT_CONTEXT}.srs_excerpt",
            f"{MCP_QA_PROJECT_CONTEXT}.test_data_sample",
            f"{MCP_QA_PROJECT_CONTEXT}.dom_distill",
        }
    ),
    ROLE_CRITIC: frozenset(
        {
            f"{MCP_QA_COMPRESS}.compress",
            f"{MCP_QA_CREW_MEMORY}.put_artifact",
            f"{MCP_QA_CREW_MEMORY}.get_summary",
            f"{MCP_QA_CREW_MEMORY}.get_full",
            f"{MCP_QA_PROJECT_CONTEXT}.case_brief",
        }
    ),
}


@dataclass
class BundledMcpRegistry:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    compress: QaCompressServer = field(init=False)
    crew_memory: QaCrewMemoryServer = field(init=False)
    project_context: QaProjectContextServer = field(init=False)
    _started: bool = False

    def __post_init__(self) -> None:
        self.compress = QaCompressServer(compressor=self.compressor)
        self.crew_memory = QaCrewMemoryServer(compressor=self.compressor)
        self.project_context = QaProjectContextServer(compressor=self.compressor)

    def start(self) -> None:
        self._started = True
        logger.info("bundled_mcp_ready servers=%s", [MCP_QA_COMPRESS, MCP_QA_CREW_MEMORY, MCP_QA_PROJECT_CONTEXT])

    def stop(self) -> None:
        self.crew_memory.clear()
        self._started = False

    @property
    def ready(self) -> bool:
        return self._started

    def invoke(
        self,
        server: str,
        tool: str,
        args: dict[str, Any] | None = None,
        role: str | None = None,
    ) -> dict[str, Any]:
        if not self._started:
            self.start()
        if role is not None and not self.allowed(role, server, tool):
            raise PermissionError(f"role {role!r} not allowed to call {server}.{tool}")
        payload = args or {}
        if server == MCP_QA_COMPRESS:
            result = self.compress.invoke(tool, payload)
        elif server == MCP_QA_CREW_MEMORY:
            result = self.crew_memory.invoke(tool, payload)
        elif server == MCP_QA_PROJECT_CONTEXT:
            result = self.project_context.invoke(tool, payload)
        else:
            raise KeyError(f"unknown bundled MCP server: {server}")
        if tool != "compress" and "content" in result and isinstance(result.get("content"), str):
            # Dual-compress tool results before returning to LLM callers.
            if len(result["content"]) > 400:
                packed = self.compress.compress(result["content"])
                result = {**result, "content": packed["compressed"], "content_hash": packed["hash"]}
        return result

    def allowed(self, role: str, server: str, tool: str) -> bool:
        key = f"{server}.{tool}"
        allowed = _ROLE_TOOLS.get(role, frozenset())
        return key in allowed

    def health(self) -> dict[str, Any]:
        return {
            "bundled": True,
            "ready": self._started,
            "servers": [MCP_QA_COMPRESS, MCP_QA_CREW_MEMORY, MCP_QA_PROJECT_CONTEXT],
        }
