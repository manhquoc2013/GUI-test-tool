from __future__ import annotations

from ai_qa_test_studio.constants import COPILOT_LEAN_TAIL_MAX_TOKENS, COPILOT_SUMMARY_MAX_CHARS
from ai_qa_test_studio.core.token_budget import ContextCompressor, TokenBudget


def test_summary_clip_respects_max_chars() -> None:
    budget = TokenBudget()
    text = budget.summarize_history_line("word " * 200)
    assert len(text) <= 200


def test_lean_tail_constant_and_compress() -> None:
    assert COPILOT_LEAN_TAIL_MAX_TOKENS >= 500
    compressor = ContextCompressor()
    big = "x" * 5000
    out = compressor.compress_text(big)
    assert len(out) <= len(big)
    assert COPILOT_SUMMARY_MAX_CHARS == 160
