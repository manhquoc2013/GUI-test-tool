from __future__ import annotations

from ai_qa_test_studio.constants import COMPACTOR_SNAPSHOT_MARKER
from ai_qa_test_studio.core.token_budget import ContextCompactor


def test_pin_survives_compaction() -> None:
    compactor = ContextCompactor()
    pinned = ContextCompactor.pin("CRITICAL FINGERPRINT DATA " * 20, "fp")
    messages = [
        {"role": "user", "content": "old chatter " * 40},
        {"role": "assistant", "content": pinned},
        {"role": "user", "content": "latest question"},
    ]
    out = compactor.compact_messages(messages, soft_cap=200)
    assert any(ContextCompactor.is_pinned(str(m["content"])) for m in out)
    assert out[-1]["content"] == "latest question"


def test_keeps_system_testcase_latest_snapshots_and_last_tools() -> None:
    compactor = ContextCompactor()
    system = {"role": "system", "content": "You are QA Studio."}
    case = {
        "role": "user",
        "content": ContextCompactor.pin("TestCase TC1 steps…", "testcase"),
    }
    snap0 = {
        "role": "tool",
        "content": f"{COMPACTOR_SNAPSHOT_MARKER}\n" + ("node " * 80),
    }
    snap1 = {
        "role": "tool",
        "content": f"{COMPACTOR_SNAPSHOT_MARKER}\n" + ("button " * 80),
    }
    snap2 = {
        "role": "tool",
        "content": f"{COMPACTOR_SNAPSHOT_MARKER}\n" + ("input " * 80),
    }
    tool_old = {
        "role": "assistant",
        "content": "tool_call browser_click ref=e1 detail=" + ("x" * 400),
    }
    tool_a = {"role": "assistant", "content": "tool_call browser_fill ref=e2"}
    tool_b = {"role": "tool", "content": "tool_result ok filled"}
    latest = {"role": "user", "content": "continue binding"}
    out = compactor.compact_messages(
        [system, case, snap0, tool_old, snap1, tool_a, tool_b, snap2, latest],
        soft_cap=50_000,
    )
    contents = [str(m["content"]) for m in out]
    assert contents[0] == system["content"]
    assert ContextCompactor.is_pinned(contents[1])
    # t-2 (snap0) stubbed; t-1 (snap1) + t-0 (snap2) kept full
    assert any("stub:browser_snapshot" in c and "t-2" in c for c in contents)
    assert snap1["content"] in contents
    assert snap2["content"] in contents
    assert tool_a["content"] in contents
    assert tool_b["content"] in contents
    # older tool pair is summarized (not kept full)
    assert tool_old["content"] not in contents
    assert contents[-1] == latest["content"]


def test_stub_from_t2_keeps_t1_full() -> None:
    compactor = ContextCompactor()
    messages = [
        {"role": "tool", "content": f"{COMPACTOR_SNAPSHOT_MARKER}\nage0-full"},
        {"role": "tool", "content": f"{COMPACTOR_SNAPSHOT_MARKER}\nage1-full"},
        {"role": "tool", "content": f"{COMPACTOR_SNAPSHOT_MARKER}\nage2-latest"},
        {"role": "user", "content": "now"},
    ]
    out = compactor.compact_messages(messages, soft_cap=50_000)
    body = "\n".join(str(m["content"]) for m in out)
    assert "age2-latest" in body
    assert "age1-full" in body
    assert "stub:browser_snapshot" in body
    assert "age0-full" not in body
