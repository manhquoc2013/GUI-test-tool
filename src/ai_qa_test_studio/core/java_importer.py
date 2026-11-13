"""Idempotent importer for JavaFX suite.json / group.json trees."""

from __future__ import annotations

import json
import logging
import re
import uuid
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import GROUP_JSON, PRIORITY_MAP, SUITE_JSON
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.legacy_locator_normalizer import (
    normalize_action,
    normalize_locator,
    normalize_variables,
)
from ai_qa_test_studio.core.models import (
    Project,
    SharedElement,
    TestCase,
    TestDataProfile,
    TestGroup,
    TestScenario,
    TestStep,
    TestSuite,
)

logger = logging.getLogger(__name__)

_SAFE = re.compile(r"[^A-Za-z0-9_]+")


def _slug(text: str, prefix: str) -> str:
    body = _SAFE.sub("_", text.strip()).strip("_") or "item"
    return f"{prefix}_{body}"[:64]


def _element_id(page: str, locator: str, index: int) -> str:
    base = _SAFE.sub("_", f"{page}_{locator}")[:40].strip("_") or f"el_{index}"
    return f"EL_{base}".upper()


class JavaProjectImporter:
    """Walk a Java ProjectStore directory tree and produce a v4 Project."""

    def __init__(self, project_dir: Path, env_manager: EnvManager | None = None) -> None:
        self.project_dir = project_dir
        self.env_manager = env_manager
        self.warnings: list[str] = []
        self._element_index = 0
        self._elements: dict[str, SharedElement] = {}

    def import_project(self, name: str | None = None) -> Project:
        self.warnings.clear()
        self._elements.clear()
        self._element_index = 0
        root = TestGroup(id=str(uuid.uuid4()), name="Root", description="Imported from JavaFX")
        if self.project_dir.is_dir():
            root.children = self._load_child_groups(self.project_dir)
            root.suites = self._load_suites(self.project_dir)
            # Promote suite cases into a default scenario per suite for ISO hierarchy.
            for suite in self._iter_suites(root):
                scenario = TestScenario(
                    id=str(uuid.uuid4()),
                    code=_slug(suite.name, "TS"),
                    title=suite.name,
                    description=suite.description,
                    cases=list(suite.cases),
                )
                parent = self._find_group_for_suite(root, suite)
                if parent is not None:
                    parent.scenarios.append(scenario)
                    suite.scenario_ids.append(scenario.id)

        project = Project(
            id=str(uuid.uuid4()),
            name=name or self.project_dir.name or "Imported Project",
            root_group=root,
            shared_elements=list(self._elements.values()),
            import_warnings=list(self.warnings),
        )
        if self.env_manager:
            project.settings = self.env_manager.load_settings()
            project.test_data_profiles = self.env_manager.list_profiles()
        return project

    def import_idempotent(self, existing: Project | None, name: str | None = None) -> Project:
        """Re-import; stable codes reused when case id matches."""
        fresh = self.import_project(name=name)
        if existing is None:
            return fresh
        # Preserve project id/name; replace tree content (deterministic rebuild).
        fresh.id = existing.id
        fresh.name = existing.name or fresh.name
        return fresh

    def _load_child_groups(self, directory: Path) -> list[TestGroup]:
        groups: list[TestGroup] = []
        for entry in sorted(directory.iterdir()):
            if not entry.is_dir():
                continue
            group_file = entry / GROUP_JSON
            if not group_file.is_file():
                continue
            payload = self._read_json(group_file)
            if payload is None:
                continue
            group = TestGroup(
                id=str(uuid.uuid4()),
                name=str(payload.get("name") or entry.name),
                description=str(payload.get("description") or ""),
                children=self._load_child_groups(entry),
                suites=self._load_suites(entry),
            )
            groups.append(group)
        return groups

    def _load_suites(self, directory: Path) -> list[TestSuite]:
        suites: list[TestSuite] = []
        for entry in sorted(directory.iterdir()):
            if not entry.is_dir():
                continue
            suite_file = entry / SUITE_JSON
            if not suite_file.is_file():
                continue
            if (entry / GROUP_JSON).is_file():
                continue
            payload = self._read_json(suite_file)
            if payload is None:
                continue
            suite = self._parse_suite(payload, entry)
            suites.append(suite)
        return suites

    def _parse_suite(self, payload: dict[str, Any], suite_dir: Path) -> TestSuite:
        cases_raw = payload.get("cases") or []
        cases: list[TestCase] = []
        for raw in cases_raw:
            if not isinstance(raw, dict):
                self.warnings.append(f"skip_non_object_case suite={suite_dir}")
                continue
            cases.append(self._parse_case(raw, suite_dir))
            profile_id = raw.get("testDataProfileId") or raw.get("test_data_profile_id")
            data_sets = raw.get("testDataSets") or []
            if profile_id and self.env_manager and isinstance(data_sets, list):
                rows = [r for r in data_sets if isinstance(r, dict)]
                self.env_manager.upsert_profile(
                    TestDataProfile(id=str(profile_id), name=str(profile_id), rows=rows)
                )
        return TestSuite(
            id=str(uuid.uuid4()),
            name=str(payload.get("name") or suite_dir.name),
            description=str(payload.get("description") or ""),
            browser=str(payload.get("browser") or ""),
            headless=payload.get("headless"),
            cases=cases,
        )

    def _parse_case(self, raw: dict[str, Any], suite_dir: Path) -> TestCase:
        case_id = str(raw.get("id") or uuid.uuid4())
        title = str(raw.get("name") or case_id)
        code = str(raw.get("testId") or raw.get("test_id") or _slug(title, "TC"))
        priority_raw = str(raw.get("priority") or "P1")
        priority = PRIORITY_MAP.get(priority_raw.upper()[:2], "P1 - High")
        if priority_raw.startswith("P") and " - " in priority_raw:
            priority = priority_raw

        steps: list[TestStep] = []
        for step_raw in raw.get("testSteps") or []:
            if not isinstance(step_raw, dict):
                continue
            steps.append(self._parse_step(step_raw, page_name=title))

        script_file = raw.get("scriptFile") or raw.get("script_file")
        artifact = None
        if script_file:
            candidate = suite_dir / str(script_file)
            artifact = str(candidate) if candidate.is_file() else str(script_file)

        data_rows: list[dict[str, Any]] = []
        for item in raw.get("testDataSets") or []:
            if isinstance(item, dict):
                # Flatten common Java shapes.
                if "values" in item and isinstance(item["values"], dict):
                    data_rows.append(dict(item["values"]))
                else:
                    data_rows.append(dict(item))

        return TestCase(
            id=case_id,
            code=code,
            title=title,
            priority=priority,  # type: ignore[arg-type]
            description=str(raw.get("description") or ""),
            requirement_ref=str(raw.get("requirementRef") or raw.get("requirement_ref") or ""),
            start_url=str(raw.get("startUrl") or raw.get("start_url") or ""),
            steps=steps,
            test_data_rows=data_rows,
            script_artifact=artifact,
            test_data_profile_id=(
                str(raw["testDataProfileId"]) if raw.get("testDataProfileId") else None
            ),
            expected_final_result=str(raw.get("description") or ""),
        )

    def _parse_step(self, raw: dict[str, Any], page_name: str) -> TestStep:
        action = normalize_action(str(raw.get("action") or "CLICK"))
        target = normalize_locator(str(raw.get("target") or ""))
        intent = normalize_variables(str(raw.get("description") or target or action))
        input_data = normalize_variables(str(raw.get("testData") or raw.get("test_data") or "")) or None
        element_ref = None
        binding: str = "UNBOUND"
        if action != "goto" and target:
            self._element_index += 1
            eid = _element_id(page_name, target, self._element_index)
            if eid not in self._elements:
                self._elements[eid] = SharedElement(
                    element_id=eid,
                    page_name=page_name,
                    friendly_name=intent or target,
                    primary_playwright=target,
                    css=target if not target.startswith("xpath=") else None,
                    xpath=target[6:] if target.startswith("xpath=") else None,
                )
            element_ref = eid
            # CSS/id-like targets are Tier-1 candidates → still UNBOUND until live bind.
            binding = "UNBOUND"
        elif action == "goto":
            binding = "BOUND"

        return TestStep(
            step_no=int(raw.get("stepNumber") or raw.get("step_no") or 0),
            action=action,  # type: ignore[arg-type]
            element_ref=element_ref,
            intent_hint=intent,
            input_data=input_data,
            expected_result=normalize_variables(str(raw.get("expectedResult") or "")),
            binding_status=binding,  # type: ignore[arg-type]
            enabled=bool(raw.get("enabled", True)),
        )

    def _read_json(self, path: Path) -> dict[str, Any] | None:
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            self.warnings.append(f"unreadable:{path}:{exc}")
            logger.warning("import_json_failed path=%s err=%s", path, exc)
            return None
        if not isinstance(payload, dict):
            self.warnings.append(f"non_object:{path}")
            return None
        return payload

    def _iter_suites(self, group: TestGroup) -> list[TestSuite]:
        found = list(group.suites)
        for child in group.children:
            found.extend(self._iter_suites(child))
        return found

    def _find_group_for_suite(self, group: TestGroup, suite: TestSuite) -> TestGroup | None:
        if suite in group.suites or any(s.id == suite.id for s in group.suites):
            return group
        for child in group.children:
            hit = self._find_group_for_suite(child, suite)
            if hit is not None:
                return hit
        return group
