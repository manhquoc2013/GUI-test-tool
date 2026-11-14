from __future__ import annotations

from ai_qa_test_studio.agent.auto_binder import _heuristic_bind


def test_heuristic_bind_returns_none_when_no_token_match() -> None:
    element_map = {
        "e1": {"name": "banner logo", "css": "#logo"},
        "e2": {"name": "footer link", "css": "a.footer"},
    }
    assert _heuristic_bind("click submit button", element_map) is None


def test_heuristic_bind_returns_matching_element_id() -> None:
    element_map = {
        "e1": {"name": "banner logo", "css": "#logo"},
        "e2": {"name": "submit button", "css": "#submit"},
    }
    assert _heuristic_bind("submit button", element_map) == "e2"


def test_heuristic_bind_empty_map_returns_none() -> None:
    assert _heuristic_bind("anything", {}) is None
