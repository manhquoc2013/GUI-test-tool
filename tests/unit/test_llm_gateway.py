from __future__ import annotations

import asyncio
import sys
from types import ModuleType
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.constants import LLM_PROVIDER_GEMINI, LLM_PROVIDER_OFFLINE


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


@pytest.mark.asyncio
async def test_gemini_cancel_closes_stream() -> None:
    closed = {"n": 0}

    class _FakeStream:
        def __init__(self) -> None:
            self._n = 0

        def __aiter__(self) -> _FakeStream:
            return self

        async def __anext__(self) -> Any:
            self._n += 1
            if self._n > 5:
                raise StopAsyncIteration
            chunk = MagicMock()
            chunk.text = f"p{self._n}"
            return chunk

        async def aclose(self) -> None:
            closed["n"] += 1

    fake_stream = _FakeStream()
    fake_genai = ModuleType("google.genai")
    fake_client = MagicMock()
    fake_client.aio.models.generate_content_stream = AsyncMock(return_value=fake_stream)
    fake_genai.Client = MagicMock(return_value=fake_client)  # type: ignore[attr-defined]

    google_mod = ModuleType("google")
    google_mod.genai = fake_genai  # type: ignore[attr-defined]

    gw = UnifiedLLMGateway(
        provider=LLM_PROVIDER_GEMINI, api_key="k", model="gemini-2.0-flash"
    )
    cancel = asyncio.Event()
    parts: list[str] = []
    prev_google = sys.modules.get("google")
    prev_genai = sys.modules.get("google.genai")
    sys.modules["google"] = google_mod
    sys.modules["google.genai"] = fake_genai
    try:
        async for part in gw.stream_chat(
            [ChatMessage(role="user", content="hi")],
            max_tokens=32,
            cancel=cancel,
        ):
            parts.append(part)
            cancel.set()
    finally:
        if prev_google is None:
            sys.modules.pop("google", None)
        else:
            sys.modules["google"] = prev_google
        if prev_genai is None:
            sys.modules.pop("google.genai", None)
        else:
            sys.modules["google.genai"] = prev_genai
    assert parts
    assert closed["n"] >= 1
