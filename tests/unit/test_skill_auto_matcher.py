from __future__ import annotations

from ai_qa_test_studio.agent.builtin_skills import builtin_skill_catalog
from ai_qa_test_studio.agent.skill_auto_matcher import SkillAutoMatcher
from ai_qa_test_studio.core.enums import IntentType


def test_matcher_max_two_and_scores() -> None:
    matcher = SkillAutoMatcher(builtin_skill_catalog())
    matches = matcher.match(
        "write playwright python script for login",
        IntentType.TEST_SCRIPT_GEN,
        workspace_signals=["pytest", "python"],
    )
    assert 1 <= len(matches) <= 2
    assert matches[0].skill.skill_id.startswith("playwright_")


def test_skill_pin_overrides() -> None:
    matcher = SkillAutoMatcher(builtin_skill_catalog())
    matches = matcher.match(
        "hello @skill:iso29119_case_author",
        IntentType.CHAT_FAST,
    )
    assert any(m.skill.skill_id == "iso29119_case_author" and m.pinned for m in matches)


def test_no_circular_import_matcher_enums_only() -> None:
    import ast
    from pathlib import Path

    path = (
        Path(__file__).resolve().parents[2]
        / "src"
        / "ai_qa_test_studio"
        / "agent"
        / "skill_auto_matcher.py"
    )
    tree = ast.parse(path.read_text(encoding="utf-8"))
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module:
            assert "intent_router" not in node.module
