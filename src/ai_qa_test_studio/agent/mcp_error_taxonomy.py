"""Classify MCP / Playwright tool failures for SmartMCPToolExecutor retry policy."""

from __future__ import annotations

import re
from enum import StrEnum

from ai_qa_test_studio.constants import (
    MCP_ERR_BAD_ARGS,
    MCP_ERR_DOM_CHANGED,
    MCP_ERR_FATAL,
    MCP_ERR_STALE_REF,
    MCP_ERR_TRANSIENT,
)


class McpErrorClass(StrEnum):
    TRANSIENT = MCP_ERR_TRANSIENT
    STALE_REF = MCP_ERR_STALE_REF
    BAD_ARGS = MCP_ERR_BAD_ARGS
    DOM_CHANGED = MCP_ERR_DOM_CHANGED
    FATAL = MCP_ERR_FATAL


_TRANSIENT_RE = re.compile(
    r"timeout|timed out|ECONNRESET|connection reset|temporarily|retry|unavailable|429|503",
    re.IGNORECASE,
)
_STALE_RE = re.compile(
    r"stale|detached|not attached|Target closed|Execution context was destroyed|"
    r"Element is not attached|node is not visible",
    re.IGNORECASE,
)
_BAD_ARGS_RE = re.compile(
    r"invalid argument|unknown tool|missing required|TypeError|ValidationError|bad request|400",
    re.IGNORECASE,
)
_DOM_RE = re.compile(
    r"strict mode violation|resolved to \d+ elements|waiting for selector|"
    r"element\(s\) not found|No node found|Locator\.|not visible",
    re.IGNORECASE,
)


def classify_mcp_error(exc: BaseException | str) -> McpErrorClass:
    text = str(exc)
    if _STALE_RE.search(text):
        return McpErrorClass.STALE_REF
    if _BAD_ARGS_RE.search(text):
        return McpErrorClass.BAD_ARGS
    if _DOM_RE.search(text):
        return McpErrorClass.DOM_CHANGED
    if _TRANSIENT_RE.search(text):
        return McpErrorClass.TRANSIENT
    return McpErrorClass.FATAL


def is_retryable(error_class: McpErrorClass) -> bool:
    return error_class in {
        McpErrorClass.TRANSIENT,
        McpErrorClass.STALE_REF,
        McpErrorClass.DOM_CHANGED,
    }
