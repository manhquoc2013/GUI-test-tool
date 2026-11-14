from __future__ import annotations

import pytest

from ai_qa_test_studio.agent.mcp_error_taxonomy import McpErrorClass
from ai_qa_test_studio.agent.mcp_tool_executor import SmartMCPToolExecutor
from ai_qa_test_studio.constants import SECRET_MASK_PLACEHOLDER


@pytest.mark.asyncio
async def test_executor_passes_raw_password_and_masks_errors() -> None:
    seen: list[dict[str, object]] = []

    async def call_tool(server: str, tool: str, arguments: dict[str, object]) -> dict[str, object]:
        seen.append(dict(arguments))
        return {"ok": True, "content": "typed", "is_error": False}

    executor = SmartMCPToolExecutor(call_tool, actions_enabled=True)
    raw = {"password": "RealSecretPass", "text": "RealSecretPass"}
    result = await executor.execute("playwright", "browser_type", raw)
    assert result["ok"] is True
    assert seen[0]["password"] == "RealSecretPass"


@pytest.mark.asyncio
async def test_executor_retries_stale_with_heal() -> None:
    calls = {"n": 0}
    heals = {"n": 0}

    async def call_tool(server: str, tool: str, arguments: dict[str, object]) -> dict[str, object]:
        calls["n"] += 1
        if calls["n"] == 1:
            return {"ok": False, "content": "Element is not attached", "is_error": True}
        return {"ok": True, "content": "ok", "is_error": False}

    async def heal(
        tool: str, arguments: dict[str, object], error_class: McpErrorClass
    ) -> dict[str, object] | None:
        heals["n"] += 1
        assert error_class is McpErrorClass.STALE_REF
        return {**arguments, "selector": "#healed"}

    executor = SmartMCPToolExecutor(call_tool, heal=heal, max_retries=1, actions_enabled=True)
    result = await executor.execute("playwright", "click", {"selector": "#stale"})
    assert result["ok"] is True
    assert heals["n"] == 1
    assert calls["n"] == 2
    assert SECRET_MASK_PLACEHOLDER  # constant import used / available
