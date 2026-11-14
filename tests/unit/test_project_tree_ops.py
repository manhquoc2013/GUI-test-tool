"""Pure unit tests for project_tree_ops."""

from __future__ import annotations

from ai_qa_test_studio.core.models import (
    Project,
    TestCase,
    TestGroup,
    TestScenario,
    TestSuite,
)
from ai_qa_test_studio.core.project_tree_ops import (
    collect_cases,
    delete_node,
    rename_node,
)


def _sample_project() -> Project:
    case_a = TestCase(id="c1", code="TC1", title="Case One")
    case_b = TestCase(id="c2", code="TC2", title="Case Two")
    case_c = TestCase(id="c3", code="TC3", title="Case Three")
    scenario = TestScenario(id="sc1", code="TS1", title="Scenario One", cases=[case_a])
    suite = TestSuite(id="su1", name="Suite One", cases=[case_b])
    nested_suite = TestSuite(id="su2", name="Nested", cases=[case_c])
    child = TestGroup(id="g2", name="Child Group", scenarios=[], suites=[nested_suite])
    root = TestGroup(id="g1", name="Root", scenarios=[scenario], suites=[suite], children=[child])
    return Project(id="p1", name="Demo", root_group=root)


def test_collect_cases_group_recursive() -> None:
    project = _sample_project()
    ids = [c.id for c in collect_cases(project.root_group)]
    assert ids == ["c1", "c2", "c3"]


def test_rename_case_and_suite() -> None:
    project = _sample_project()
    assert rename_node(project, "c1", "Renamed Case")
    assert project.root_group.scenarios[0].cases[0].title == "Renamed Case"
    assert rename_node(project, "su1", "Suite Renamed")
    assert project.root_group.suites[0].name == "Suite Renamed"
    assert not rename_node(project, "c1", "   ")
    assert not rename_node(project, "c1", "Renamed Case")


def test_delete_protect_root_and_cascade() -> None:
    project = _sample_project()
    blocked = delete_node(project, "g1", protect_root=True)
    assert not blocked.deleted
    assert blocked.removed_case_ids == set()
    result = delete_node(project, "g2", protect_root=True)
    assert result.deleted
    assert result.removed_case_ids == {"c3"}
    assert project.root_group.children == []
    case_del = delete_node(project, "c1", protect_root=True)
    assert case_del.deleted
    assert case_del.removed_case_ids == {"c1"}
    assert project.root_group.scenarios[0].cases == []
