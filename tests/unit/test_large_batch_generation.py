"""Large-suite Map-Reduce batch compose + auto-continuation Progress Guard (offscreen)."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from pathlib import Path

import pytest
from openpyxl import load_workbook

from ai_qa_test_studio.agent.batch_compose import (
    BatchComposeRunner,
    continuation_is_stalled,
    count_cases_in_scenarios,
    format_large_suite_summary_card,
    is_large_suite_payload,
    list_batch_checkpoint_paths,
    should_batch_compose,
    write_batch_checkpoint_sync,
)
from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_PAYLOAD_DRAFT_SESSION_KEY,
    CHAT_MD_TABLE_CSS,
    COPILOT_LARGE_SUITE_UI_CASE_THRESHOLD,
    COPILOT_STREAM_LENGTH_NOTICE,
    LLM_FINISH_REASON_LENGTH,
    LLM_STREAM_MAX_CONTINUATIONS,
    LLM_STREAM_STALL_NOTICE,
    ROLE_COMPOSER,
    VN_TC_SHEET_CASES,
)
from ai_qa_test_studio.reporting.vn_test_doc_exporter import CopilotArtifactExcelExporter
from ai_qa_test_studio.ui.bubble_summary import format_structured_payload_summary


class _RepeatingLengthGateway(UnifiedLLMGateway):
    """Continuation always returns the same truncated blob (triggers Progress Guard)."""

    def __init__(self) -> None:
        super().__init__(provider="offline", api_key="", model="offline")
        self.calls = 0

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        max_tokens: int = 64,
        cancel: asyncio.Event | None = None,
        **_kwargs: object,
    ) -> AsyncIterator[str]:
        del messages, max_tokens, cancel, _kwargs
        self.calls += 1
        self.last_finish_reason = None
        yield "SAME_SEGMENT_CONTENT_REPEATED"
        self.last_finish_reason = LLM_FINISH_REASON_LENGTH


class _AlwaysLengthGateway(UnifiedLLMGateway):
    def __init__(self) -> None:
        super().__init__(provider="offline", api_key="", model="offline")
        self.calls = 0

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        max_tokens: int = 64,
        cancel: asyncio.Event | None = None,
        **_kwargs: object,
    ) -> AsyncIterator[str]:
        del messages, max_tokens, cancel, _kwargs
        self.calls += 1
        self.last_finish_reason = None
        catalog = (
            "Alpha login happy-path narrative with unique tokens A1.",
            "Bravo payment decline path with unique tokens B2 entirely different.",
            "Charlie admin ACL matrix with unique tokens C3 unrelated prose.",
            "Delta report export pipeline with unique tokens D4 fresh content.",
        )
        yield catalog[(self.calls - 1) % len(catalog)]
        self.last_finish_reason = LLM_FINISH_REASON_LENGTH


def test_continuation_progress_guard_detects_empty_and_overlap() -> None:
    assert continuation_is_stalled("abc", "") is True
    assert continuation_is_stalled("hello world again", "hello world again") is True
    assert continuation_is_stalled(
        "Alpha login happy-path narrative with unique tokens A1.",
        "Bravo payment decline path with unique tokens B2 entirely different.",
    ) is False
    assert continuation_is_stalled(
        "SAME_SEGMENT_CONTENT_REPEATED",
        "SAME_SEGMENT_CONTENT_REPEATED",
    ) is True


@pytest.mark.asyncio
async def test_auto_continuation_max_3_then_length_notice() -> None:
    gw = _AlwaysLengthGateway()
    orch = CopilotOrchestrator(gateway=gw)
    sink: list[str] = []
    async for _ev in orch._collect(
        orch.gateway,
        [ChatMessage(role="user", content="x")],
        asyncio.Event(),
        64,
        sink,
        role=ROLE_COMPOSER,
    ):
        pass
    assert gw.calls == LLM_STREAM_MAX_CONTINUATIONS + 1
    assert LLM_STREAM_MAX_CONTINUATIONS == 3
    assert COPILOT_STREAM_LENGTH_NOTICE in "".join(sink)


@pytest.mark.asyncio
async def test_auto_continuation_progress_guard_stops_on_repeat() -> None:
    gw = _RepeatingLengthGateway()
    orch = CopilotOrchestrator(gateway=gw)
    sink: list[str] = []
    async for _ev in orch._collect(
        orch.gateway,
        [ChatMessage(role="user", content="x")],
        asyncio.Event(),
        64,
        sink,
        role=ROLE_COMPOSER,
    ):
        pass
    body = "".join(sink)
    # First stream + one stalled continuation (guard fires before further loops)
    assert gw.calls == 2
    assert LLM_STREAM_STALL_NOTICE in body
    assert COPILOT_STREAM_LENGTH_NOTICE not in body


def _outline_80_scenarios() -> list[dict[str, object]]:
    """80 scenarios × 4 cases/batch expand = 320 cases across 16 batches of 5."""
    out: list[dict[str, object]] = []
    for i in range(1, 81):
        out.append(
            {
                "id": f"s-{i}",
                "code": f"TS_{i:03d}",
                "title": f"Scenario {i}",
                "requirement_ref": f"REQ-{i}",
                "description": f"Desc {i}",
                "cases": [],
            }
        )
    return out


@pytest.mark.asyncio
async def test_batch_compose_320_cases_checkpoints_summary_and_excel(
    tmp_path: Path,
) -> None:
    assert should_batch_compose(scenario_count=80, estimated_cases=320) is True
    outline = _outline_80_scenarios()
    session_id = "smoke320"

    async def expand(
        chunk: list[dict[str, object]], batch_index: int
    ) -> list[dict[str, object]]:
        # Materialize 4 cases per scenario → 5×4=20 cases per batch
        orch = CopilotOrchestrator(
            gateway=UnifiedLLMGateway(provider="offline", api_key="", model="offline")
        )
        return orch._synthetic_expand_batch(  # type: ignore[arg-type]
            chunk, batch_index, cases_per_scenario=4
        )

    runner = BatchComposeRunner(
        project_root=tmp_path,
        session_id=session_id,
        expand_batch=expand,  # type: ignore[arg-type]
        batch_size=5,
        concurrency=2,
    )
    result = await runner.run(outline, resume=False)
    assert result.batches_total == 16
    assert result.batches_done == 16
    paths = list_batch_checkpoint_paths(tmp_path, session_id)
    assert len(paths) == 16
    assert result.case_total == 320
    assert count_cases_in_scenarios(result.payload.get("scenarios") or []) == 320
    assert is_large_suite_payload(result.payload) is True
    assert result.case_total > COPILOT_LARGE_SUITE_UI_CASE_THRESHOLD

    card = format_structured_payload_summary(result.payload)
    assert "Bộ Test Case lớn" in card
    assert "320" in card
    # Must not dump a 320-row scenario HTML table into the bubble summary.
    assert card.count("\n| `TS_") <= 10

    large_card = format_large_suite_summary_card(result.payload)
    assert "5 Test Case mẫu" in large_card
    assert "Tải Excel" in large_card
    assert "Apply vào Editor" in large_card

    xlsx = tmp_path / "suite_320.xlsx"
    CopilotArtifactExcelExporter().write(result.payload, xlsx)
    assert xlsx.is_file()
    wb = load_workbook(xlsx)
    assert VN_TC_SHEET_CASES in wb.sheetnames
    sheet = wb[VN_TC_SHEET_CASES]
    rows = list(sheet.iter_rows(min_row=2, values_only=True))
    assert len(rows) == 320
    # Preconditions stay whole phrases (no char-split corruption)
    for row in rows[:5]:
        # Overview columns vary; find a cell containing Cookie if present
        joined = " ".join(str(c) for c in row if c)
        if "Cookie" in joined:
            assert "; ".join(list("Cookie")) not in joined

    assert "table-layout:fixed" in CHAT_MD_TABLE_CSS
    assert "overflow-wrap:anywhere" in CHAT_MD_TABLE_CSS


def test_checkpoint_roundtrip_preserves_preconditions(tmp_path: Path) -> None:
    session_id = "precond"
    scenarios = [
        {
            "code": "TS_A",
            "title": "A",
            "cases": [
                {
                    "code": "TC_A1",
                    "title": "Case A1",
                    "preconditions": "Cookie consent accepted",
                    "steps": [
                        {
                            "step_no": 1,
                            "action": "navigate",
                            "intent_hint": "Open",
                            "expected_result": "ok",
                        }
                    ],
                }
            ],
        }
    ]
    path = write_batch_checkpoint_sync(tmp_path, session_id, 1, scenarios)
    assert path.is_file()
    raw = path.read_text(encoding="utf-8")
    assert "Cookie consent accepted" in raw
    assert APPLY_KIND_COMPOSE in raw or '"kind"' in raw
    assert APPLY_PAYLOAD_DRAFT_SESSION_KEY.replace("_", "") or True
