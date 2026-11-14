"""Bundled in-process MCP tool servers (zero user install)."""

from __future__ import annotations

from ai_qa_test_studio.agent.mcp_servers.qa_compress import QaCompressServer
from ai_qa_test_studio.agent.mcp_servers.qa_crew_memory import QaCrewMemoryServer
from ai_qa_test_studio.agent.mcp_servers.qa_ingest import QaIngestServer
from ai_qa_test_studio.agent.mcp_servers.qa_midscene import QaMidsceneServer
from ai_qa_test_studio.agent.mcp_servers.qa_persistent_memory import QaPersistentMemoryServer
from ai_qa_test_studio.agent.mcp_servers.qa_playwright import QaPlaywrightServer
from ai_qa_test_studio.agent.mcp_servers.qa_project_context import QaProjectContextServer

__all__ = [
    "QaCompressServer",
    "QaCrewMemoryServer",
    "QaIngestServer",
    "QaMidsceneServer",
    "QaPersistentMemoryServer",
    "QaPlaywrightServer",
    "QaProjectContextServer",
]
