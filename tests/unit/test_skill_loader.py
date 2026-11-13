from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader


def test_skill_catalog_includes_seed_skills() -> None:
    loader = ProgressiveSkillLoader(Path("skills"))
    catalog = loader.get_compact_catalog()
    assert "web-e2e-functional" in catalog
    assert "boundary-value-analysis" in loader.registry
    detail = loader.load_skill_context("web-e2e-functional")
    assert "E2E" in detail or "end-to-end" in detail.lower() or "nghiệp vụ" in detail
