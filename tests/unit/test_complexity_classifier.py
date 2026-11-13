from __future__ import annotations

import pytest

from ai_qa_test_studio.agent.complexity_classifier import ComplexityClassifier
from ai_qa_test_studio.constants import (
    COMPLEXITY_COMPLEX,
    COMPLEXITY_SIMPLE,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
)


@pytest.mark.asyncio
async def test_short_prompt_is_simple() -> None:
    level = await ComplexityClassifier().classify("ok", COPILOT_MODE_CASE, {})
    assert level == COMPLEXITY_SIMPLE


@pytest.mark.asyncio
async def test_long_srs_compose_bva_is_complex() -> None:
    srs = "A" * 900
    level = await ComplexityClassifier().classify(
        "Tạo kịch bản từ SRS và thêm BVA biên",
        COPILOT_MODE_COMPOSE,
        {"srs": srs, "include_dom": True, "include_case": True},
    )
    assert level == COMPLEXITY_COMPLEX
