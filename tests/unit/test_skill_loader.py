from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.constants import MICRO_TOOL_LOAD_SKILL


def test_skill_catalog_includes_seed_skills() -> None:
    loader = ProgressiveSkillLoader(Path("skills"))
    catalog = loader.get_compact_catalog()
    assert "web-e2e-functional" in catalog
    assert "boundary-value-analysis" in loader.registry
    detail = loader.load_skill_context("web-e2e-functional")
    assert "E2E" in detail or "end-to-end" in detail.lower() or "nghiệp vụ" in detail


def test_frontmatter_description_preferred_over_heading(tmp_path: Path) -> None:
    skill_dir = tmp_path / "demo-skill"
    skill_dir.mkdir()
    (skill_dir / "SKILL.md").write_text(
        "---\nname: demo-skill\ndescription: Frontmatter blurb for catalog\n---\n\n"
        "# Wrong Heading\n\nBody instructions here.\n",
        encoding="utf-8",
    )
    loader = ProgressiveSkillLoader(tmp_path)
    assert loader.registry["demo-skill"].description == "Frontmatter blurb for catalog"
    catalog = loader.get_compact_catalog()
    assert "Frontmatter blurb for catalog" in catalog
    assert "Wrong Heading" not in catalog


def test_invoke_load_skill_tool_returns_body(tmp_path: Path) -> None:
    skill_dir = tmp_path / "demo-skill"
    skill_dir.mkdir()
    (skill_dir / "SKILL.md").write_text(
        "---\nname: demo-skill\ndescription: Demo\n---\n\n# Demo\n\nDo the thing.\n",
        encoding="utf-8",
    )
    loader = ProgressiveSkillLoader(tmp_path)
    body = loader.invoke_tool(MICRO_TOOL_LOAD_SKILL, {"skill_name": "demo-skill"})
    assert "Do the thing" in body
    missing = loader.invoke_tool(MICRO_TOOL_LOAD_SKILL, {"skill_name": "nope"})
    assert "không tồn tại" in missing.lower() or "không tồn tại" in missing


def test_load_skill_soft_fails_corrupt_usage_json(tmp_path: Path) -> None:
    skill_dir = tmp_path / "demo-skill"
    skill_dir.mkdir()
    (skill_dir / "SKILL.md").write_text(
        "---\nname: demo-skill\ndescription: Demo\n---\n\n# Demo\n\nBody.\n",
        encoding="utf-8",
    )
    (skill_dir / ".usage.json").write_text("{not-json", encoding="utf-8")
    loader = ProgressiveSkillLoader(tmp_path)
    body = loader.load_skill_context("demo-skill", domain="login")
    assert "Body" in body
    assert "Kinh nghiệm thực chiến" not in body
    loader.record_usage_note("demo-skill", "login", "prefer role locator")
    reloaded = loader.load_skill_context("demo-skill", domain="login")
    assert "prefer role locator" in reloaded
