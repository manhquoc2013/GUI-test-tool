from __future__ import annotations

import pytest
import tiktoken

from ai_qa_test_studio.constants import TOKEN_FALLBACK_CHARS_PER_TOKEN
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


def test_token_budget_survives_missing_tiktoken_plugins(monkeypatch: pytest.MonkeyPatch) -> None:
    """Frozen builds without tiktoken_ext must not crash the UI at startup."""

    def _missing(*args: object, **kwargs: object) -> tiktoken.Encoding:
        raise ValueError("Unknown encoding o200k_base.\nPlugins found: []")

    monkeypatch.setattr(tiktoken, "encoding_for_model", _missing)
    monkeypatch.setattr(tiktoken, "get_encoding", _missing)

    budget = TokenBudget()

    assert budget.count("hello world") > 0
    capped = budget.truncate_to_cap("x" * 10_000, cap=10)
    assert len(capped) == 10 * TOKEN_FALLBACK_CHARS_PER_TOKEN
