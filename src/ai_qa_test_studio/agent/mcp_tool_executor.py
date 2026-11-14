"""SmartMCPToolExecutor — raw args in → call → heal on STALE/DOM → retry; mask outbound only."""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Any

from ai_qa_test_studio.agent.mcp_error_taxonomy import (
    McpErrorClass,
    classify_mcp_error,
    is_retryable,
)
from ai_qa_test_studio.constants import MCP_ACTIONS_ENABLED, MCP_EXECUTOR_MAX_RETRIES
from ai_qa_test_studio.core.secret_masker import get_secret_masker

logger = logging.getLogger(__name__)

CallToolFn = Callable[[str, str, dict[str, Any]], Awaitable[dict[str, Any]]]
HealFn = Callable[[str, dict[str, Any], McpErrorClass], Awaitable[dict[str, Any] | None]]


class SmartMCPToolExecutor:
    """Host-side MCP tool runner. Never mutates inbound Playwright args for masking."""

    def __init__(
        self,
        call_tool: CallToolFn,
        *,
        heal: HealFn | None = None,
        max_retries: int = MCP_EXECUTOR_MAX_RETRIES,
        actions_enabled: bool = MCP_ACTIONS_ENABLED,
    ) -> None:
        self._call_tool = call_tool
        self._heal = heal
        self._max_retries = max_retries
        self.actions_enabled = actions_enabled

    async def execute(
        self,
        server: str,
        tool: str,
        arguments: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        raw_args = dict(arguments or {})
        masked = get_secret_masker().mask_mapping(raw_args)
        logger.debug(
            "smart_executor_call server=%s tool=%s args=%s enabled=%s",
            server,
            tool,
            masked,
            self.actions_enabled,
        )
        if not self.actions_enabled and server in {"playwright", "midscene"}:
            # Observe-only until flag flips; still allow list/locate-style if caller opts in.
            logger.info("mcp_actions_gated server=%s tool=%s", server, tool)

        last_error: BaseException | None = None
        working_args = raw_args
        for attempt in range(self._max_retries + 1):
            try:
                result = await self._call_tool(server, tool, working_args)
                if result.get("is_error") or result.get("ok") is False:
                    err_text = str(result.get("content") or "tool_error")
                    error_class = classify_mcp_error(err_text)
                    if attempt < self._max_retries and is_retryable(error_class):
                        healed = await self._maybe_heal(tool, working_args, error_class)
                        if healed is not None:
                            working_args = healed
                        continue
                    return {
                        **result,
                        "error_class": error_class.value,
                        "attempts": attempt + 1,
                    }
                return {**result, "attempts": attempt + 1, "error_class": None}
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                error_class = classify_mcp_error(exc)
                logger.warning(
                    "smart_executor_error server=%s tool=%s class=%s attempt=%s err=%s",
                    server,
                    tool,
                    error_class.value,
                    attempt + 1,
                    get_secret_masker().mask_text(str(exc)),
                )
                if attempt < self._max_retries and is_retryable(error_class):
                    healed = await self._maybe_heal(tool, working_args, error_class)
                    if healed is not None:
                        working_args = healed
                    continue
                return {
                    "ok": False,
                    "content": get_secret_masker().mask_text(str(exc)),
                    "is_error": True,
                    "error_class": error_class.value,
                    "attempts": attempt + 1,
                }
        return {
            "ok": False,
            "content": get_secret_masker().mask_text(str(last_error or "unknown")),
            "is_error": True,
            "error_class": McpErrorClass.FATAL.value,
            "attempts": self._max_retries + 1,
        }

    async def _maybe_heal(
        self,
        tool: str,
        arguments: dict[str, Any],
        error_class: McpErrorClass,
    ) -> dict[str, Any] | None:
        if self._heal is None:
            return None
        if error_class not in {McpErrorClass.STALE_REF, McpErrorClass.DOM_CHANGED}:
            return None
        try:
            return await self._heal(tool, arguments, error_class)
        except Exception as exc:  # noqa: BLE001
            logger.warning("smart_executor_heal_failed err=%s", exc)
            return None
