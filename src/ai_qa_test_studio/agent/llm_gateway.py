"""Unified async streaming LLM gateway (OpenAI-compatible, Gemini, offline)."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass

from ai_qa_test_studio.constants import (
    COPILOT_CHAT_MAX_TOKENS,
    LLM_PROVIDER_GEMINI,
    LLM_PROVIDER_OFFLINE,
    LLM_PROVIDER_OPENAI_COMPAT,
)
from ai_qa_test_studio.core.models import AiSettings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ChatMessage:
    role: str
    content: str


def resolve_llm_provider(ai: AiSettings) -> str:
    provider = (ai.provider or "").strip().lower()
    if provider in {LLM_PROVIDER_OFFLINE, LLM_PROVIDER_OPENAI_COMPAT, LLM_PROVIDER_GEMINI}:
        if provider != LLM_PROVIDER_OFFLINE and not (ai.api_key_ref or "").strip():
            return LLM_PROVIDER_OFFLINE
        return provider
    if not (ai.api_key_ref or "").strip():
        return LLM_PROVIDER_OFFLINE
    model = (ai.model or "").lower()
    if "gemini" in model:
        return LLM_PROVIDER_GEMINI
    return LLM_PROVIDER_OPENAI_COMPAT


class UnifiedLLMGateway:
    def __init__(
        self,
        provider: str,
        api_key: str,
        model: str,
        base_url: str | None = None,
    ) -> None:
        self.provider = provider
        self.api_key = api_key
        self.model = model or "offline"
        self.base_url = base_url

    @classmethod
    def from_settings(cls, ai: AiSettings) -> UnifiedLLMGateway:
        provider = resolve_llm_provider(ai)
        return cls(
            provider=provider,
            api_key=ai.api_key_ref,
            model=ai.model or ("gemini-2.0-flash" if provider == LLM_PROVIDER_GEMINI else "gpt-4o"),
            base_url=ai.base_url or None,
        )

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        max_tokens: int = COPILOT_CHAT_MAX_TOKENS,
        cancel: asyncio.Event | None = None,
    ) -> AsyncIterator[str]:
        if cancel is not None and cancel.is_set():
            return
        if self.provider == LLM_PROVIDER_OFFLINE:
            async for chunk in self._stream_offline(messages, cancel=cancel):
                yield chunk
            return
        if self.provider == LLM_PROVIDER_GEMINI:
            async for chunk in self._stream_gemini(messages, max_tokens=max_tokens, cancel=cancel):
                yield chunk
            return
        async for chunk in self._stream_openai(messages, max_tokens=max_tokens, cancel=cancel):
            yield chunk

    async def _stream_offline(
        self,
        messages: list[ChatMessage],
        cancel: asyncio.Event | None,
    ) -> AsyncIterator[str]:
        last = next((m.content for m in reversed(messages) if m.role == "user"), "")
        text = (
            f"[offline] Đã nhận yêu cầu: {last[:240]}\n"
            "1) Phân tích ngữ cảnh\n2) Soạn artifact\n3) Rà soát Critic"
        )
        for word in text.split(" "):
            if cancel is not None and cancel.is_set():
                return
            yield word + " "
            await asyncio.sleep(0)

    async def _stream_openai(
        self,
        messages: list[ChatMessage],
        max_tokens: int,
        cancel: asyncio.Event | None,
    ) -> AsyncIterator[str]:
        try:
            from openai import AsyncOpenAI
        except ImportError as exc:
            raise RuntimeError("openai package required for OpenAI-compatible provider") from exc
        client = AsyncOpenAI(api_key=self.api_key, base_url=self.base_url or None)
        payload = [{"role": m.role, "content": m.content} for m in messages]
        stream = await client.chat.completions.create(
            model=self.model,
            messages=payload,  # type: ignore[arg-type]
            max_tokens=max_tokens,
            stream=True,
        )
        async for chunk in stream:  # type: ignore[union-attr]
            if cancel is not None and cancel.is_set():
                close = getattr(stream, "close", None)
                if callable(close):
                    result = close()
                    if asyncio.iscoroutine(result):
                        await result
                return
            delta = chunk.choices[0].delta.content if chunk.choices else None
            if delta:
                yield delta

    async def _stream_gemini(
        self,
        messages: list[ChatMessage],
        max_tokens: int,
        cancel: asyncio.Event | None,
    ) -> AsyncIterator[str]:
        try:
            from google import genai
        except ImportError as exc:
            raise RuntimeError("google-genai package required for Gemini provider") from exc
        client = genai.Client(api_key=self.api_key)
        sys_prompt = next((m.content for m in messages if m.role == "system"), "")
        contents = "\n\n".join(f"{m.role.upper()}: {m.content}" for m in messages if m.role != "system")
        try:
            response = await client.aio.models.generate_content_stream(
                model=self.model,
                contents=contents,
                config={"system_instruction": sys_prompt, "max_output_tokens": max_tokens},
            )
            async for chunk in response:
                if cancel is not None and cancel.is_set():
                    return
                text = getattr(chunk, "text", None)
                if text:
                    yield text
        except Exception as exc:  # noqa: BLE001
            logger.error("gemini_stream_failed err=%s", exc)
            raise
