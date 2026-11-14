"""Stream cut by finish_reason=length / TimeoutError must settle Điều phối viên + show notice."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from typing import Any
from unittest.mock import MagicMock

import pytest
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
from ai_qa_test_studio.constants import (
    COPILOT_STREAM_ERROR_PREFIX,
    COPILOT_STREAM_LENGTH_NOTICE,
    EVENT_CHUNK,
    EVENT_ERROR,
    LLM_FINISH_REASON_LENGTH,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
)
from ai_qa_test_studio.ui.chat_markdown import looks_like_markdown_table, markdown_to_bubble_html
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


class _LengthGateway(UnifiedLLMGateway):
    def __init__(self) -> None:
        super().__init__(provider="offline", api_key="", model="offline")

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        max_tokens: int = 64,
        cancel: asyncio.Event | None = None,
    ) -> AsyncIterator[str]:
        del messages, max_tokens, cancel
        self.last_finish_reason = None
        yield "Kịch bản đăng nhập bị cắt giữa "
        self.last_finish_reason = LLM_FINISH_REASON_LENGTH


class _TimeoutGateway(UnifiedLLMGateway):
    def __init__(self) -> None:
        super().__init__(provider="offline", api_key="", model="offline")

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        max_tokens: int = 64,
        cancel: asyncio.Event | None = None,
    ) -> AsyncIterator[str]:
        del messages, max_tokens, cancel
        self.last_finish_reason = None
        yield "Bắt đầu…"
        raise TimeoutError("LLM stream timed out")


@pytest.mark.asyncio
async def test_collect_appends_length_notice() -> None:
    orch = CopilotOrchestrator(gateway=_LengthGateway())
    sink: list[str] = []
    events = [
        ev
        async for ev in orch._collect(
            orch.gateway,
            [ChatMessage(role="user", content="x")],
            asyncio.Event(),
            64,
            sink,
            role=ROLE_COMPOSER,
        )
    ]
    body = "".join(sink)
    assert "Kịch bản đăng nhập bị cắt giữa" in body
    assert COPILOT_STREAM_LENGTH_NOTICE in body
    assert any(ev.kind == EVENT_CHUNK and COPILOT_STREAM_LENGTH_NOTICE in (ev.text or "") for ev in events)


@pytest.mark.asyncio
async def test_collect_surfaces_timeout_as_error_event() -> None:
    orch = CopilotOrchestrator(gateway=_TimeoutGateway())
    sink: list[str] = []
    events = [
        ev
        async for ev in orch._collect(
            orch.gateway,
            [ChatMessage(role="user", content="x")],
            asyncio.Event(),
            64,
            sink,
            role=ROLE_COMPOSER,
        )
    ]
    assert any(ev.kind == EVENT_ERROR for ev in events)
    joined = "".join(sink)
    assert COPILOT_STREAM_ERROR_PREFIX in joined
    assert "timed out" in joined.lower()


def test_length_notice_settles_coordinator_typing_dots(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    pane.set_streaming_status("Điều phối viên đang chọn bước tiếp…", role=ROLE_COORDINATOR)
    pane.begin_role(ROLE_COMPOSER)
    composer = pane._rows[1]
    assert composer.is_typing()
    assert composer.typing.is_running()

    truncated = "Bảng scenario bị cắt giữa câu" + COPILOT_STREAM_LENGTH_NOTICE
    pane.end_role(ROLE_COMPOSER, truncated, summary=truncated)
    pane.on_turn_finished()

    for row in pane._rows:
        assert row.is_typing() is False
        assert row.typing.is_running() is False
    assert COPILOT_STREAM_LENGTH_NOTICE in (composer._text or "")
    pane.close()


def test_timeout_error_appended_to_bubble_and_stops_dots(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    pane.begin_role(ROLE_COMPOSER)
    notice = f"{COPILOT_STREAM_ERROR_PREFIX}TimeoutError: LLM stream timed out"
    pane.append_error_to_active_bubble(notice, role=ROLE_COMPOSER)
    pane.on_turn_finished()

    composer = pane._rows[1]
    assert notice in (composer._text or "")
    for row in pane._rows:
        assert row.typing.is_running() is False
        assert row.is_typing() is False
    pane.close()


@pytest.mark.asyncio
async def test_openai_stream_records_finish_reason_length() -> None:
    class _Chunk:
        def __init__(self, content: str | None, finish_reason: str | None) -> None:
            delta = MagicMock()
            delta.content = content
            choice = MagicMock()
            choice.delta = delta
            choice.finish_reason = finish_reason
            self.choices = [choice]

    class _Stream:
        def __aiter__(self) -> _Stream:
            self._items = iter(
                [
                    _Chunk("Hello ", None),
                    _Chunk("world", LLM_FINISH_REASON_LENGTH),
                ]
            )
            return self

        async def __anext__(self) -> Any:
            try:
                return next(self._items)
            except StopIteration as exc:
                raise StopAsyncIteration from exc

    async def _create(*args: Any, **kwargs: Any) -> _Stream:
        del args, kwargs
        return _Stream()

    client = MagicMock()
    client.chat.completions.create = _create
    gw = UnifiedLLMGateway(provider="openai_compatible", api_key="k", model="m")
    gw._openai_client = client

    parts: list[str] = []
    async for part in gw.stream_chat([ChatMessage(role="user", content="hi")], max_tokens=8):
        parts.append(part)
    assert "".join(parts) == "Hello world"
    assert gw.last_finish_reason == LLM_FINISH_REASON_LENGTH


def test_composer_markdown_table_renders_html(qapp: QApplication) -> None:
    del qapp
    table = (
        "| ID | Title |\n"
        "|----|-------|\n"
        "| TS01 | Login happy |\n"
        "| TS02 | Login negative |"
    )
    assert looks_like_markdown_table(table)
    html = markdown_to_bubble_html(table)
    assert "<table" in html.lower()

    row = ChatMessageRow("m1", ROLE_COMPOSER, table)
    assert row.body.textFormat() == Qt.TextFormat.RichText
    assert "<table" in row.body.text().lower()
    row.close()
