from __future__ import annotations

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.constants import MCP_QA_COMPRESS, MCP_QA_CREW_MEMORY, MCP_QA_PROJECT_CONTEXT


def test_compress_retrieve_round_trip() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    packed = reg.invoke(MCP_QA_COMPRESS, "compress", {"content": "hello " * 200})
    assert packed["hash"]
    got = reg.invoke(MCP_QA_COMPRESS, "retrieve", {"hash": packed["hash"]})
    assert got["found"] is True


def test_crew_memory_put_summary_full() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    put = reg.invoke(
        MCP_QA_CREW_MEMORY,
        "put_artifact",
        {"role": "composer", "kind": "draft", "text": "Full artifact content " * 40},
    )
    ref = put["ref"]
    summary = reg.invoke(MCP_QA_CREW_MEMORY, "get_summary", {"ref": ref})
    full = reg.invoke(MCP_QA_CREW_MEMORY, "get_full", {"ref": ref})
    assert summary["found"] is True
    assert len(summary["summary"]) <= 160
    assert "Full artifact" in full["content"]


def test_case_brief_smaller_than_full_dump() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    huge = '{"id":"c1","title":"Login","steps":[' + ",".join(['{"a":1}'] * 50) + "]}"
    reg.project_context.bind(case_json=huge)
    brief = reg.invoke(MCP_QA_PROJECT_CONTEXT, "case_brief", {"case_id": "c1"})
    assert len(brief["brief"]) < len(huge)
