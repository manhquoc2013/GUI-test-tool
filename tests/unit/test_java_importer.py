from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.java_importer import JavaProjectImporter
from ai_qa_test_studio.core.storage import ProjectStorage


def test_import_all_legacy_suites_idempotent(tmp_path: Path) -> None:
    project_dir = Path("data/project")
    assert project_dir.is_dir()
    env = EnvManager(tmp_path / "config")
    env.load_settings()
    importer = JavaProjectImporter(project_dir, env)
    first = importer.import_project(name="Legacy")
    second = importer.import_idempotent(first, name="Legacy")
    assert second.id == first.id
    assert len(first.shared_elements) == len(second.shared_elements)
    suites = first.root_group.suites
    # nested suites also exist
    total_cases = sum(len(s.cases) for s in _all_suites(first.root_group))
    assert total_cases > 0
    # locator normalization applied on at least one step
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


def _all_suites(group):  # type: ignore[no-untyped-def]
    found = list(group.suites)
    for child in group.children:
        found.extend(_all_suites(child))
    return found
