"""YAML project tree round-trip + migrate from project.v4.json."""

from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.constants import DEFAULT_V4_PROJECT_FILENAME, PROJECT_YAML_FILENAME
from ai_qa_test_studio.core.models import (
    Project,
    SharedElement,
    TestCase,
    TestGroup,
    TestScenario,
    TestStep,
)
from ai_qa_test_studio.core.project_yaml import ProjectYamlStorage


def _sample_project() -> Project:
    case = TestCase(
        id="case-1",
        code="TC01",
        title="Tạo hồ sơ",
        steps=[
            TestStep(step_no=1, action="click", intent_hint="Mở form", binding_status="UNBOUND"),
        ],
    )
    scenario = TestScenario(id="sc-1", code="TS01", title="Luồng chính", cases=[case])
    root = TestGroup(id="root", name="Root", scenarios=[scenario])
    return Project(
        id="proj-1",
        name="Demo",
        root_group=root,
        shared_elements=[
            SharedElement(
                element_id="EL_BTN_SAVE",
                page_name="FormPage",
                friendly_name="Save",
                css="#save",
            )
        ],
    )


def test_yaml_round_trip(tmp_path: Path) -> None:
    store = ProjectYamlStorage(tmp_path)
    project = _sample_project()
    path = store.save(project)
    assert path.name == PROJECT_YAML_FILENAME
    assert (tmp_path / "suites").is_dir()
    assert (tmp_path / "elements").is_dir()
    loaded = store.load()
    assert loaded is not None
    assert loaded.id == "proj-1"
    assert loaded.name == "Demo"
    assert len(loaded.root_group.scenarios) == 1
    assert loaded.root_group.scenarios[0].cases[0].code == "TC01"
    assert loaded.root_group.scenarios[0].cases[0].steps[0].action == "click"
    assert len(loaded.shared_elements) == 1
    assert loaded.shared_elements[0].element_id == "EL_BTN_SAVE"


def test_migrate_v4_json_to_yaml(tmp_path: Path) -> None:
    project = _sample_project()
    v4 = tmp_path / DEFAULT_V4_PROJECT_FILENAME
    v4.write_text(project.model_dump_json(indent=2), encoding="utf-8")

    store = ProjectYamlStorage(tmp_path)
    loaded = store.load()
    assert loaded is not None
    assert loaded.name == "Demo"
    assert (tmp_path / PROJECT_YAML_FILENAME).is_file()
    bak = tmp_path / f"{DEFAULT_V4_PROJECT_FILENAME}.bak"
    assert bak.is_file()
    raw = json.loads(bak.read_text(encoding="utf-8"))
    assert raw["id"] == "proj-1"
    assert not v4.is_file()
