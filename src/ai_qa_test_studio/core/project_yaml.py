"""Git-friendly YAML project tree (Hybrid Local-First scenarios layer)."""

from __future__ import annotations

import json
import logging
import re
import shutil
from pathlib import Path
from typing import Any

import yaml

from ai_qa_test_studio.constants import (
    DEFAULT_V4_PROJECT_FILENAME,
    PROJECT_DATA_DIRNAME,
    PROJECT_ELEMENTS_DIRNAME,
    PROJECT_SUITES_DIRNAME,
    PROJECT_YAML_FILENAME,
)
from ai_qa_test_studio.core.models import (
    Project,
    SharedElement,
    TestGroup,
    TestScenario,
    TestSuite,
)

logger = logging.getLogger(__name__)

_SAFE_NAME = re.compile(r"[^A-Za-z0-9._-]+")


def _safe_filename(raw: str, fallback: str) -> str:
    cleaned = _SAFE_NAME.sub("_", (raw or "").strip()).strip("._")
    return cleaned or fallback


class ProjectYamlStorage:
    """Persist Project as ``project.yaml`` + ``suites/`` + ``elements/`` (+ optional ``data/``)."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)
        self.project_yaml = self.root / PROJECT_YAML_FILENAME
        self.suites_dir = self.root / PROJECT_SUITES_DIRNAME
        self.elements_dir = self.root / PROJECT_ELEMENTS_DIRNAME
        self.data_dir = self.root / PROJECT_DATA_DIRNAME
        self.v4_json = self.root / DEFAULT_V4_PROJECT_FILENAME

    def save(self, project: Project) -> Path:
        self._clear_tree_dirs()
        self.suites_dir.mkdir(parents=True, exist_ok=True)
        self.elements_dir.mkdir(parents=True, exist_ok=True)
        self.data_dir.mkdir(parents=True, exist_ok=True)

        suite_refs = self._write_groups(project.root_group, self.suites_dir / "root")
        element_refs = self._write_elements(project.shared_elements)
        data_refs = self._write_test_data(project)

        meta: dict[str, Any] = {
            "id": project.id,
            "name": project.name,
            "description": project.description,
            "environments": [e.model_dump(mode="json") for e in project.environments],
            "settings": project.settings.model_dump(mode="json"),
            "import_warnings": list(project.import_warnings),
            "root_group": {
                "id": project.root_group.id,
                "name": project.root_group.name,
                "description": project.root_group.description,
                "refs": suite_refs,
            },
            "elements": element_refs,
            "test_data": data_refs,
        }
        tmp = self.project_yaml.with_suffix(".tmp")
        tmp.write_text(
            yaml.safe_dump(meta, allow_unicode=True, sort_keys=False),
            encoding="utf-8",
        )
        tmp.replace(self.project_yaml)
        return self.project_yaml

    def load(self) -> Project | None:
        if self.project_yaml.is_file():
            return self._load_yaml_tree()
        if self.v4_json.is_file():
            return self._migrate_v4_json()
        return None

    def _migrate_v4_json(self) -> Project:
        payload = json.loads(self.v4_json.read_text(encoding="utf-8"))
        project = Project.model_validate(payload)
        bak = self.root / f"{DEFAULT_V4_PROJECT_FILENAME}.bak"
        shutil.copy2(self.v4_json, bak)
        self.save(project)
        try:
            self.v4_json.unlink()
        except OSError as exc:
            logger.warning("v4_json_unlink_failed path=%s err=%s", self.v4_json, exc)
        return project

    def _load_yaml_tree(self) -> Project:
        meta = yaml.safe_load(self.project_yaml.read_text(encoding="utf-8"))
        if not isinstance(meta, dict):
            raise ValueError("project.yaml root must be a mapping")
        root_meta = meta.get("root_group") or {}
        root = TestGroup(
            id=str(root_meta.get("id") or "root"),
            name=str(root_meta.get("name") or "Root"),
            description=str(root_meta.get("description") or ""),
        )
        self._hydrate_group(root, root_meta.get("refs") or {})
        elements = self._load_elements(meta.get("elements") or [])
        project = Project(
            id=str(meta.get("id") or ""),
            name=str(meta.get("name") or "Untitled"),
            description=str(meta.get("description") or ""),
            root_group=root,
            shared_elements=elements,
            import_warnings=list(meta.get("import_warnings") or []),
        )
        if isinstance(meta.get("environments"), list):
            from ai_qa_test_studio.core.models import EnvironmentProfile

            project.environments = [
                EnvironmentProfile.model_validate(item)
                for item in meta["environments"]
                if isinstance(item, dict)
            ]
        if isinstance(meta.get("settings"), dict):
            from ai_qa_test_studio.core.models import AppSettings

            project.settings = AppSettings.model_validate(meta["settings"])
        self._load_test_data(project, meta.get("test_data") or [])
        return project

    def _clear_tree_dirs(self) -> None:
        for path in (self.suites_dir, self.elements_dir, self.data_dir):
            if path.is_dir():
                shutil.rmtree(path)

    def _write_groups(self, group: TestGroup, base: Path) -> dict[str, Any]:
        base.mkdir(parents=True, exist_ok=True)
        refs: dict[str, Any] = {
            "scenarios": [],
            "suites": [],
            "children": [],
        }
        for scenario in group.scenarios:
            rel = self._write_scenario(scenario, base)
            refs["scenarios"].append(rel)
        for suite in group.suites:
            rel = self._write_suite(suite, base)
            refs["suites"].append(rel)
        for child in group.children:
            child_dir = base / _safe_filename(child.name or child.id, child.id)
            child_refs = self._write_groups(child, child_dir)
            refs["children"].append(
                {
                    "id": child.id,
                    "name": child.name,
                    "description": child.description,
                    "path": str(child_dir.relative_to(self.root)),
                    "refs": child_refs,
                }
            )
        return refs

    def _write_scenario(self, scenario: TestScenario, base: Path) -> str:
        name = _safe_filename(scenario.code or scenario.id, scenario.id)
        path = base / f"scenario_{name}.yaml"
        payload = scenario.model_dump(mode="json")
        path.write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")
        return str(path.relative_to(self.root))

    def _write_suite(self, suite: TestSuite, base: Path) -> str:
        name = _safe_filename(suite.name or suite.id, suite.id)
        path = base / f"suite_{name}.yaml"
        payload = suite.model_dump(mode="json")
        path.write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")
        return str(path.relative_to(self.root))

    def _write_elements(self, elements: list[SharedElement]) -> list[str]:
        refs: list[str] = []
        for element in elements:
            name = _safe_filename(element.element_id, element.element_id)
            path = self.elements_dir / f"{name}.yaml"
            path.write_text(
                yaml.safe_dump(element.model_dump(mode="json"), allow_unicode=True, sort_keys=False),
                encoding="utf-8",
            )
            refs.append(str(path.relative_to(self.root)))
        return refs

    def _write_test_data(self, project: Project) -> list[str]:
        refs: list[str] = []
        for profile in project.test_data_profiles:
            name = _safe_filename(profile.name or profile.id, profile.id)
            path = self.data_dir / f"{name}.yaml"
            path.write_text(
                yaml.safe_dump(profile.model_dump(mode="json"), allow_unicode=True, sort_keys=False),
                encoding="utf-8",
            )
            refs.append(str(path.relative_to(self.root)))
        return refs

    def _hydrate_group(self, group: TestGroup, refs: dict[str, Any]) -> None:
        for rel in refs.get("scenarios") or []:
            path = self.root / str(rel)
            if not path.is_file():
                continue
            payload = yaml.safe_load(path.read_text(encoding="utf-8"))
            group.scenarios.append(TestScenario.model_validate(payload))
        for rel in refs.get("suites") or []:
            path = self.root / str(rel)
            if not path.is_file():
                continue
            payload = yaml.safe_load(path.read_text(encoding="utf-8"))
            group.suites.append(TestSuite.model_validate(payload))
        for child_meta in refs.get("children") or []:
            if not isinstance(child_meta, dict):
                continue
            child = TestGroup(
                id=str(child_meta.get("id") or ""),
                name=str(child_meta.get("name") or ""),
                description=str(child_meta.get("description") or ""),
            )
            self._hydrate_group(child, child_meta.get("refs") or {})
            group.children.append(child)

    def _load_elements(self, refs: list[Any]) -> list[SharedElement]:
        elements: list[SharedElement] = []
        for rel in refs:
            path = self.root / str(rel)
            if not path.is_file():
                continue
            payload = yaml.safe_load(path.read_text(encoding="utf-8"))
            elements.append(SharedElement.model_validate(payload))
        return elements

    def _load_test_data(self, project: Project, refs: list[Any]) -> None:
        from ai_qa_test_studio.core.models import TestDataProfile

        profiles: list[TestDataProfile] = []
        for rel in refs:
            path = self.root / str(rel)
            if not path.is_file():
                continue
            payload = yaml.safe_load(path.read_text(encoding="utf-8"))
            profiles.append(TestDataProfile.model_validate(payload))
        project.test_data_profiles = profiles
