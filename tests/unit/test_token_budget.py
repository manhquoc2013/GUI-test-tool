from __future__ import annotations

from ai_qa_test_studio.core.token_budget import ContextCompressor, TokenBudget


def test_token_budget_truncates() -> None:
    budget = TokenBudget()
    huge = "word " * 5000
    out = budget.truncate_to_cap(huge, cap=50)
    assert budget.count(out) <= 50


def test_sliding_messages() -> None:
    comp = ContextCompressor()
    messages = [
        {"role": "user", "content": "old " * 100},
        {"role": "assistant", "content": "reply"},
        {"role": "user", "content": "current"},
    ]
    slid = comp.sliding_messages(messages)
    assert slid[-1]["content"] == "current"
    assert len(slid[0]["content"]) <= 200
