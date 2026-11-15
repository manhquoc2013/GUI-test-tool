"""JavaFX suite/group importer — uses an in-repo fixture (data/project is gitignored)."""

from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.constants import GROUP_JSON, SUITE_JSON
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.java_importer import JavaProjectImporter
from ai_qa_test_studio.core.models import TestGroup, TestSuite
from ai_qa_test_studio.core.storage import ProjectStorage


def _write_legacy_tree(root: Path) -> None:
    """Minimal Java ProjectStore layout: Group → Suite → Case with NAVIGATE + CLICK."""
    group_dir = root / "DemoGroup"
    suite_dir = group_dir / "DemoSuite"
    suite_dir.mkdir(parents=True)
    (group_dir / GROUP_JSON).write_text(
        json.dumps({"name": "DemoGroup", "description": "fixture"}),
        encoding="utf-8",
    )
    suite_payload = {
        "name": "DemoSuite",
        "description": "fixture suite",
        "browser": "CHROMIUM",
        "headless": True,
        "cases": [
            {
                "id": "case-fixture-1",
                "name": "Open and click",
                "testId": "TC_FIXTURE_01",
                "priority": "P1",
                "description": "Smoke case",
                "startUrl": "https://example.com",
                "testSteps": [
                    {
                        "stepNumber": 1,
                        "action": "NAVIGATE",
                        "target": "https://example.com",
                        "description": "Go to home",
                        "expectedResult": "Home loads",
                    },
                    {
                        "stepNumber": 2,
                        "action": "CLICK",
                        "target": "#submit",
                        "description": "Click submit",
                        "expectedResult": "Form submits",
                    },
                ],
            }
        ],
    }
    (suite_dir / SUITE_JSON).write_text(
        json.dumps(suite_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _all_suites(group: TestGroup) -> list[TestSuite]:
    found = list(group.suites)
    for child in group.children:
        found.extend(_all_suites(child))
    return found


def test_import_all_legacy_suites_idempotent(tmp_path: Path) -> None:
    project_dir = tmp_path / "legacy_project"
    project_dir.mkdir()
    _write_legacy_tree(project_dir)

    env = EnvManager(tmp_path / "config")
    env.load_settings()
    importer = JavaProjectImporter(project_dir, env)
    first = importer.import_project(name="Legacy")
    second = importer.import_idempotent(first, name="Legacy")
    assert second.id == first.id
    assert len(first.shared_elements) == len(second.shared_elements)

    suites = first.root_group.suites
    total_cases = sum(len(s.cases) for s in _all_suites(first.root_group))
    assert total_cases > 0

    steps = [
        step
        for suite in _all_suites(first.root_group)
        for case in suite.cases
        for step in case.steps
    ]
    assert any(step.action == "goto" for step in steps)
    assert any(step.intent_hint for step in steps)

    storage = ProjectStorage(tmp_path / "out")
    storage.save(first)
    loaded = storage.load()
    assert loaded is not None
    assert loaded.name == first.name
    assert suites or first.root_group.children
