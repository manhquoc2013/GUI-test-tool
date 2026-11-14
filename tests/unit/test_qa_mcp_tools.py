from __future__ import annotations

import pytest

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.constants import (
    MCP_QA_COMPRESS,
    MCP_QA_CREW_MEMORY,
    MCP_QA_MIDSCENE,
    MCP_QA_PLAYWRIGHT,
    MCP_QA_PROJECT_CONTEXT,
    MCP_TOOL_LOCATE,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_PLANNER,
)


def test_compress_retrieve_round_trip() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    packed = reg.invoke(
        MCP_QA_COMPRESS, "compress", {"content": "hello " * 200}, role=ROLE_COMPOSER
    )
    assert packed["hash"]
    got = reg.invoke(
        MCP_QA_COMPRESS, "retrieve", {"hash": packed["hash"]}, role=ROLE_COMPOSER
    )
    assert got["found"] is True


def test_crew_memory_put_summary_full() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    put = reg.invoke(
        MCP_QA_CREW_MEMORY,
        "put_artifact",
        {"role": "composer", "kind": "draft", "text": "Full artifact content " * 40},
        role=ROLE_COMPOSER,
    )
    ref = put["ref"]
    summary = reg.invoke(MCP_QA_CREW_MEMORY, "get_summary", {"ref": ref}, role=ROLE_COMPOSER)
    full = reg.invoke(MCP_QA_CREW_MEMORY, "get_full", {"ref": ref}, role=ROLE_COMPOSER)
    assert summary["found"] is True
    assert len(summary["summary"]) <= 160
    assert "Full artifact" in full["content"]


def test_case_brief_smaller_than_full_dump() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    huge = '{"id":"c1","title":"Login","steps":[' + ",".join(['{"a":1}'] * 50) + "]}"
    reg.project_context.bind(case_json=huge)
    brief = reg.invoke(
        MCP_QA_PROJECT_CONTEXT, "case_brief", {"case_id": "c1"}, role=ROLE_PLANNER
    )
    assert len(brief["brief"]) < len(huge)


def test_bundled_mcp_rejects_disallowed_role() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    with pytest.raises((PermissionError, ValueError, KeyError)):
        reg.invoke(MCP_QA_COMPRESS, "stats", {}, role=ROLE_CRITIC)
    with pytest.raises((PermissionError, ValueError, KeyError)):
        reg.invoke(
            MCP_QA_PROJECT_CONTEXT,
            "test_data_sample",
            {},
            role=ROLE_PLANNER,
        )


def test_role_none_defaults_to_system_acl() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    packed = reg.invoke(MCP_QA_COMPRESS, "compress", {"content": "x" * 20})
    assert "compressed" in packed or "hash" in packed
    with pytest.raises(PermissionError):
        reg.invoke(MCP_QA_COMPRESS, "retrieve", {"hash": "missing"})


def test_midscene_locate_routed_for_system_role() -> None:
    reg = BundledMcpRegistry()
    reg.start()
    result = reg.invoke(
        MCP_QA_MIDSCENE,
        MCP_TOOL_LOCATE,
        {
            "intent": "sign in",
            "element_map": {
                "e1": {"name": "Sign in", "css": "#submit", "role": "button"},
            },
        },
    )
    assert result["css"] == "#submit"
    assert result["reason"] == "dom_intent"
    health = reg.health()
    assert MCP_QA_MIDSCENE in health["servers"]
    assert MCP_QA_PLAYWRIGHT in health["servers"]
