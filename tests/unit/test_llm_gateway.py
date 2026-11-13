from __future__ import annotations

import asyncio

import pytest

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.constants import LLM_PROVIDER_OFFLINE


@pytest.mark.asyncio
async def test_offline_stream_yields_chunks() -> None:
    gw = UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    chunks: list[str] = []
    async for part in gw.stream_chat(
        [ChatMessage(role="user", content="hello")],
        max_tokens=64,
    ):
        chunks.append(part)
    assert "".join(chunks)


@pytest.mark.asyncio
async def test_offline_stream_respects_cancel() -> None:
    gw = UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    cancel = asyncio.Event()
    cancel.set()
    parts = [p async for p in gw.stream_chat([ChatMessage(role="user", content="x")], cancel=cancel)]
    assert parts == []
