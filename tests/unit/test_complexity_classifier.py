from __future__ import annotations

import pytest

from ai_qa_test_studio.agent.complexity_classifier import ComplexityClassifier
from ai_qa_test_studio.agent.model_router import ModelRouter
from ai_qa_test_studio.constants import (
    COMPLEXITY_COMPLEX,
    COMPLEXITY_NORMAL,
    COMPLEXITY_SIMPLE,
    CONTEXT_KEY_ATTACHMENTS,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
    LLM_TASK_COMPOSE,
    LLM_TASK_PLAN,
    LLM_TIER_MID,
    LLM_TIER_STRONG,
)
from ai_qa_test_studio.core.env_manager import default_task_complexity_matrix
from ai_qa_test_studio.core.models import AiRoutingSettings, AiSettings


@pytest.mark.asyncio
async def test_short_prompt_is_simple() -> None:
    level = await ComplexityClassifier().classify("ok", COPILOT_MODE_CASE, {})
    assert level == COMPLEXITY_SIMPLE


@pytest.mark.asyncio
async def test_explain_only_stays_simple() -> None:
    level = await ComplexityClassifier().classify(
        "Giải thích lỗi bước đăng nhập vừa chạy",
        COPILOT_MODE_CASE,
        {},
    )
    assert level == COMPLEXITY_SIMPLE


@pytest.mark.asyncio
async def test_bva_request_is_at_least_normal() -> None:
    level = await ComplexityClassifier().classify(
        "Sinh thêm 5 bộ Test Data biên (BVA)",
        COPILOT_MODE_CASE,
        {"include_case": True},
    )
    assert level in {COMPLEXITY_NORMAL, COMPLEXITY_COMPLEX}


@pytest.mark.asyncio
async def test_attachment_compose_nl_is_not_simple() -> None:
    level = await ComplexityClassifier().classify(
        "làm giúp tôi bộ kiểm thử từ file này đi",
        COPILOT_MODE_CASE,
        {
            CONTEXT_KEY_ATTACHMENTS: [
                {"name": "srs.docx", "markdown": "# SRS\n" + ("x" * 200)}
            ]
        },
    )
    assert level != COMPLEXITY_SIMPLE


@pytest.mark.asyncio
async def test_long_srs_compose_bva_is_complex() -> None:
    srs = "A" * 900
    level = await ComplexityClassifier().classify(
        "Tạo kịch bản từ SRS và thêm BVA biên toàn bộ",
        COPILOT_MODE_COMPOSE,
        {"srs": srs, "include_dom": True, "include_case": True},
    )
    assert level == COMPLEXITY_COMPLEX


def test_assess_exposes_signals() -> None:
    assessment = ComplexityClassifier().assess(
        "Tạo kịch bản e2e từ SRS",
        COPILOT_MODE_COMPOSE,
        {"srs": "B" * 500},
    )
    assert assessment.score >= 2
    assert any(s.startswith("hint:") or s.startswith("mode:") for s in assessment.signals)


def test_matrix_compose_normal_uses_mid_not_strong() -> None:
    matrix = default_task_complexity_matrix()
    assert matrix[LLM_TASK_COMPOSE][COMPLEXITY_NORMAL] == LLM_TIER_MID
    assert matrix[LLM_TASK_COMPOSE][COMPLEXITY_COMPLEX] == LLM_TIER_STRONG
    assert matrix[LLM_TASK_PLAN][COMPLEXITY_COMPLEX] == LLM_TIER_STRONG


def test_router_ignores_complexity_when_disabled() -> None:
    settings = AiSettings(
        routing=AiRoutingSettings(
            complexity_enabled=False,
            task_complexity_models=default_task_complexity_matrix(),
        )
    )
    router = ModelRouter(settings=settings)
    # Without connections, offline — but tier should come from "normal" row.
    route = router.resolve(LLM_TASK_COMPOSE, COMPLEXITY_COMPLEX)
    assert route.complexity == COMPLEXITY_NORMAL
    assert route.tier == LLM_TIER_MID
