"""URL enrich + scenario_designer skill prompt expectations."""

from __future__ import annotations

import pytest

from ai_qa_test_studio.agent.builtin_skills import BUILTIN_SKILLS
from ai_qa_test_studio.agent.url_context import enrich_user_text_with_urls, extract_http_urls
from ai_qa_test_studio.constants import COPILOT_GEN_MAX_TOKENS, COPILOT_URL_BROWSER_OFF_NOTE
from ai_qa_test_studio.core.enums import IntentType


def test_extract_http_urls() -> None:
    text = "Xem https://api-docs.deepseek.com/quick_start/pricing và http://example.com/a."
    urls = extract_http_urls(text)
    assert urls[0].startswith("https://api-docs.deepseek.com/")
    assert "http://example.com/a" in urls


@pytest.mark.asyncio
async def test_enrich_without_browser_adds_note(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        "ai_qa_test_studio.agent.url_context._http_fetch_sync",
        lambda url, timeout_s=12.0: f"title for {url}",
    )
    text, note = await enrich_user_text_with_urls(
        "Thiết kế scenario cho https://example.com/docs",
        mcp=None,
    )
    assert note == COPILOT_URL_BROWSER_OFF_NOTE
    assert "URL page structure: https://example.com/docs" in text
    assert "title for https://example.com/docs" in text


def test_scenario_designer_skill_asks_for_compact_table() -> None:
    skill = next(s for s in BUILTIN_SKILLS if s.skill_id == "scenario_designer")
    assert IntentType.TEST_SCENARIO in skill.intents
    body = skill.prompt_body.lower()
    assert "12–18" in skill.prompt_body or "12-18" in skill.prompt_body
    assert "table" in body or "markdown" in body
    assert COPILOT_GEN_MAX_TOKENS == 8192
