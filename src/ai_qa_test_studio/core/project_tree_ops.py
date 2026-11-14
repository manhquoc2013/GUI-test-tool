"""Pure project-tree rename / delete / collect — no Qt imports."""

from __future__ import annotations

from dataclasses import dataclass, field

from ai_qa_test_studio.core.models import (
    Project,
    TestCase,
    TestGroup,
    TestScenario,
    TestSuite,
)

TreeNode = TestCase | TestScenario | TestSuite | TestGroup


@dataclass(frozen=True)
class DeleteResult:
    deleted: bool
    removed_case_ids: set[str] = field(default_factory=set)


def collect_cases(node: TreeNode | Project) -> list[TestCase]:
    if isinstance(node, Project):
        return collect_cases(node.root_group)
    if isinstance(node, TestCase):
        return [node]
    if isinstance(node, TestScenario):
        return list(node.cases)
    if isinstance(node, TestSuite):
        return list(node.cases)
    cases: list[TestCase] = []
    for scenario in node.scenarios:
        cases.extend(scenario.cases)
    for suite in node.suites:
        cases.extend(suite.cases)
    for child in node.children:
        cases.extend(collect_cases(child))
    return cases


def find_node(project: Project, node_id: str) -> TreeNode | None:
    root = project.root_group
    if root.id == node_id:
        return root
    return _find_in_group(root, node_id)


def _find_in_group(group: TestGroup, node_id: str) -> TreeNode | None:
    for scenario in group.scenarios:
        if scenario.id == node_id:
            return scenario
        for case in scenario.cases:
            if case.id == node_id:
                return case
    for suite in group.suites:
        if suite.id == node_id:
            return suite
        for case in suite.cases:
            if case.id == node_id:
                return case
    for child in group.children:
        if child.id == node_id:
            return child
        found = _find_in_group(child, node_id)
        if found is not None:
            return found
    return None


def node_display_name(node: TreeNode) -> str:
    if isinstance(node, (TestCase, TestScenario)):
        return node.title
    return node.name


def rename_node(project: Project, node_id: str, new_name: str) -> bool:
    name = new_name.strip()
    if not name:
        return False
    node = find_node(project, node_id)
    if node is None:
        return False
    current = node_display_name(node)
    if current == name:
        return False
    if isinstance(node, (TestCase, TestScenario)):
        node.title = name
        return True
    if isinstance(node, (TestSuite, TestGroup)):
        node.name = name
        return True
    return False


def _case_ids_under(node: TreeNode) -> set[str]:
    return {case.id for case in collect_cases(node)}


def delete_node(project: Project, node_id: str, *, protect_root: bool = True) -> DeleteResult:
    root = project.root_group
    if protect_root and root.id == node_id:
        return DeleteResult(deleted=False, removed_case_ids=set())
    if root.id == node_id:
        removed = _case_ids_under(root)
        root.scenarios.clear()
        root.suites.clear()
        root.children.clear()
        return DeleteResult(deleted=True, removed_case_ids=removed)
    removed_ids = _delete_from_group(root, node_id)
    if removed_ids is None:
        return DeleteResult(deleted=False, removed_case_ids=set())
    return DeleteResult(deleted=True, removed_case_ids=removed_ids)


def _delete_from_group(group: TestGroup, node_id: str) -> set[str] | None:
    for idx, scenario in enumerate(group.scenarios):
        if scenario.id == node_id:
            removed = _case_ids_under(scenario)
            del group.scenarios[idx]
            return removed
        for cidx, case in enumerate(scenario.cases):
            if case.id == node_id:
                del scenario.cases[cidx]
                return {case.id}
    for idx, suite in enumerate(group.suites):
        if suite.id == node_id:
            removed = _case_ids_under(suite)
            del group.suites[idx]
            return removed
        for cidx, case in enumerate(suite.cases):
            if case.id == node_id:
                del suite.cases[cidx]
                return {case.id}
    for idx, child in enumerate(group.children):
        if child.id == node_id:
            removed = _case_ids_under(child)
            del group.children[idx]
            return removed
        nested = _delete_from_group(child, node_id)
        if nested is not None:
            return nested
    return None
