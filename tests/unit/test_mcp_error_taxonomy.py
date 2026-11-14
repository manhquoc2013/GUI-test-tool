from __future__ import annotations

from ai_qa_test_studio.agent.mcp_error_taxonomy import (
    McpErrorClass,
    classify_mcp_error,
    is_retryable,
)


def test_classify_stale_and_transient() -> None:
    assert classify_mcp_error("Element is not attached to the DOM") is McpErrorClass.STALE_REF
    assert classify_mcp_error("Timeout 30000ms exceeded") is McpErrorClass.TRANSIENT
    assert classify_mcp_error("waiting for selector #x") is McpErrorClass.DOM_CHANGED
    assert classify_mcp_error("invalid argument: selector") is McpErrorClass.BAD_ARGS
    assert classify_mcp_error("boom") is McpErrorClass.FATAL


def test_retryable_set() -> None:
    assert is_retryable(McpErrorClass.TRANSIENT)
    assert is_retryable(McpErrorClass.STALE_REF)
    assert not is_retryable(McpErrorClass.FATAL)
    assert not is_retryable(McpErrorClass.BAD_ARGS)
