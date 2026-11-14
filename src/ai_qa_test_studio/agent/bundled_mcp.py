"""In-process MCP registry — auto-ready on app start, no user install."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.agent.mcp_servers.qa_compress import QaCompressServer
from ai_qa_test_studio.agent.mcp_servers.qa_crew_memory import QaCrewMemoryServer
from ai_qa_test_studio.agent.mcp_servers.qa_ingest import QaIngestServer
from ai_qa_test_studio.agent.mcp_servers.qa_midscene import QaMidsceneServer
from ai_qa_test_studio.agent.mcp_servers.qa_persistent_memory import QaPersistentMemoryServer
from ai_qa_test_studio.agent.mcp_servers.qa_playwright import QaPlaywrightServer
from ai_qa_test_studio.agent.mcp_servers.qa_project_context import QaProjectContextServer
from ai_qa_test_studio.constants import (
    MCP_QA_COMPRESS,
    MCP_QA_CREW_MEMORY,
    MCP_QA_INGEST,
    MCP_QA_MIDSCENE,
    MCP_QA_PERSISTENT_MEMORY,
    MCP_QA_PLAYWRIGHT,
    MCP_QA_PROJECT_CONTEXT,
    MCP_TOOL_DOCUMENT_TO_MARKDOWN,
    MCP_TOOL_LOCATE,
    MCP_TOOL_MEMORY_GET,
    MCP_TOOL_MEMORY_PUT,
    MCP_TOOL_MEMORY_SEARCH,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_PLANNER,
    ROLE_SYSTEM,
)
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)

_MIDSCENE_LOCATE = f"{MCP_QA_MIDSCENE}.{MCP_TOOL_LOCATE}"
_PLAYWRIGHT_SCREENSHOT = f"{MCP_QA_PLAYWRIGHT}.screenshot"
_PLAYWRIGHT_CDP_INFO = f"{MCP_QA_PLAYWRIGHT}.cdp_info"
_INGEST_DOC = f"{MCP_QA_INGEST}.{MCP_TOOL_DOCUMENT_TO_MARKDOWN}"
_INGEST_ALIAS = f"{MCP_QA_INGEST}.ingest"
_MEM_PUT = f"{MCP_QA_PERSISTENT_MEMORY}.{MCP_TOOL_MEMORY_PUT}"
_MEM_GET = f"{MCP_QA_PERSISTENT_MEMORY}.{MCP_TOOL_MEMORY_GET}"
_MEM_SEARCH = f"{MCP_QA_PERSISTENT_MEMORY}.{MCP_TOOL_MEMORY_SEARCH}"

_ROLE_TOOLS: dict[str, frozenset[str]] = {
    ROLE_SYSTEM: frozenset(
        {
            f"{MCP_QA_COMPRESS}.compress",
            f"{MCP_QA_COMPRESS}.stats",
            f"{MCP_QA_CREW_MEMORY}.get_summary",
            f"{MCP_QA_CREW_MEMORY}.get_full",
            _MIDSCENE_LOCATE,
            _PLAYWRIGHT_SCREENSHOT,
            _PLAYWRIGHT_CDP_INFO,
            _INGEST_DOC,
            _INGEST_ALIAS,
            _MEM_PUT,
            _MEM_GET,
            _MEM_SEARCH,
        }
    ),
    ROLE_PLANNER: frozenset(
        {
            f"{MCP_QA_COMPRESS}.compress",
            f"{MCP_QA_COMPRESS}.stats",
            f"{MCP_QA_CREW_MEMORY}.put_artifact",
            f"{MCP_QA_CREW_MEMORY}.get_summary",
            f"{MCP_QA_CREW_MEMORY}.get_full",
            f"{MCP_QA_PROJECT_CONTEXT}.case_brief",
            f"{MCP_QA_PROJECT_CONTEXT}.srs_excerpt",
            _MIDSCENE_LOCATE,
            _INGEST_DOC,
            _INGEST_ALIAS,
            _MEM_GET,
            _MEM_SEARCH,
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
            _MIDSCENE_LOCATE,
            _INGEST_DOC,
            _INGEST_ALIAS,
            _MEM_PUT,
            _MEM_GET,
            _MEM_SEARCH,
        }
    ),
    ROLE_CRITIC: frozenset(
        {
            f"{MCP_QA_COMPRESS}.compress",
            f"{MCP_QA_CREW_MEMORY}.put_artifact",
            f"{MCP_QA_CREW_MEMORY}.get_summary",
            f"{MCP_QA_CREW_MEMORY}.get_full",
            f"{MCP_QA_PROJECT_CONTEXT}.case_brief",
            _MIDSCENE_LOCATE,
            _MEM_GET,
            _MEM_SEARCH,
        }
    ),
}

_SERVER_LIST = (
    MCP_QA_COMPRESS,
    MCP_QA_CREW_MEMORY,
    MCP_QA_PROJECT_CONTEXT,
    MCP_QA_MIDSCENE,
    MCP_QA_PLAYWRIGHT,
    MCP_QA_INGEST,
    MCP_QA_PERSISTENT_MEMORY,
)


@dataclass
class BundledMcpRegistry:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    compress: QaCompressServer = field(init=False)
    crew_memory: QaCrewMemoryServer = field(init=False)
    project_context: QaProjectContextServer = field(init=False)
    midscene: QaMidsceneServer = field(init=False)
    playwright: QaPlaywrightServer = field(init=False)
    ingest: QaIngestServer = field(init=False)
    persistent_memory: QaPersistentMemoryServer = field(init=False)
    _started: bool = False

    def __post_init__(self) -> None:
        self.compress = QaCompressServer(compressor=self.compressor)
        self.crew_memory = QaCrewMemoryServer(compressor=self.compressor)
        self.project_context = QaProjectContextServer(compressor=self.compressor)
        self.midscene = QaMidsceneServer()
        self.playwright = QaPlaywrightServer()
        self.ingest = QaIngestServer(compressor=self.compressor)
        self.persistent_memory = QaPersistentMemoryServer()

    def start(self) -> None:
        self._started = True
        logger.info("bundled_mcp_ready servers=%s", list(_SERVER_LIST))

    def stop(self) -> None:
        self.crew_memory.clear()
        self.persistent_memory.close()
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
        resolved_role = ROLE_SYSTEM if role is None else role
        if not self.allowed(resolved_role, server, tool):
            raise PermissionError(f"role {resolved_role!r} not allowed to call {server}.{tool}")
        payload = args or {}
        if server == MCP_QA_COMPRESS:
            result = self.compress.invoke(tool, payload)
        elif server == MCP_QA_CREW_MEMORY:
            result = self.crew_memory.invoke(tool, payload)
        elif server == MCP_QA_PROJECT_CONTEXT:
            result = self.project_context.invoke(tool, payload)
        elif server == MCP_QA_MIDSCENE:
            result = self.midscene.invoke(tool, payload)
        elif server == MCP_QA_PLAYWRIGHT:
            result = self.playwright.invoke(tool, payload)
        elif server == MCP_QA_INGEST:
            result = self.ingest.invoke(tool, payload)
        elif server == MCP_QA_PERSISTENT_MEMORY:
            result = self.persistent_memory.invoke(tool, payload)
        else:
            raise KeyError(f"unknown bundled MCP server: {server}")
        if tool != "compress" and "content" in result and isinstance(result.get("content"), str):
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
            "servers": list(_SERVER_LIST),
        }
