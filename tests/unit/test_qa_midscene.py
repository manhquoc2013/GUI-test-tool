from __future__ import annotations

from ai_qa_test_studio.agent.mcp_servers.qa_midscene import QaMidsceneServer


def test_locate_finds_by_intent_tokens() -> None:
    server = QaMidsceneServer()
    element_map = {
        "e1": {"name": "Cancel", "css": "#cancel", "role": "button"},
        "e2": {"name": "Sign in", "css": "#login-submit", "role": "button"},
    }
    result = server.locate("click the sign in button", element_map)
    assert result["css"] == "#login-submit"
    assert result["element_id"] == "e2"
    assert result["reason"] == "dom_intent"
    assert 0.0 < float(result["confidence"]) <= 1.0


def test_locate_no_match_returns_empty_css() -> None:
    server = QaMidsceneServer()
    element_map = {
        "e1": {"name": "Cancel", "css": "#cancel", "role": "button"},
    }
    result = server.locate("submit payment form", element_map)
    assert result["css"] == ""
    assert float(result["confidence"]) == 0.0
    assert result["reason"] == "no_match"
