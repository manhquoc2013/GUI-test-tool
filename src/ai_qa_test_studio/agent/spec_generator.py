"""Outline-first scenario generation + chunked test-case batches."""

from __future__ import annotations

import logging
import uuid
from collections.abc import Awaitable, Callable
from typing import Any

from ai_qa_test_studio.agent.llm_json import loads_llm_json, preview_llm_text
from ai_qa_test_studio.constants import (
    LLM_JSON_ONLY_RULE,
    PRIORITY_MAP,
    SPEC_OUTLINE_COVER_RULE,
)
from ai_qa_test_studio.core.compose_mapping import (
    coerce_preconditions,
    ensure_case_test_data_rows,
    normalize_raw_step,
)
from ai_qa_test_studio.core.models import Priority, TestCase, TestScenario, TestStep, TestType
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)

LlmFn = Callable[[str], Awaitable[str]]


class SpecGenerator:
    def __init__(self, llm: LlmFn | None = None, compressor: ContextCompressor | None = None) -> None:
        self.llm = llm
        self.compressor = compressor or ContextCompressor()

    def generate_outline_sync(self, markdown_spec: str) -> list[TestScenario]:
        """UI-safe outline (no nested event loop)."""
        return self._offline_outline(markdown_spec)

    def generate_cases_sync(self, scenario: TestScenario, batch_size: int = 3) -> list[TestCase]:
        return self._offline_cases(scenario, batch_size)

    def parse_test_table_sync(self, table_markdown: str) -> list[TestScenario]:
        """Parse non-standard tabular/test prose into scenarios (LLM via async_bridge)."""
        if self.llm is None:
            return []
        from ai_qa_test_studio.core.async_bridge import run_sync

        return run_sync(self.parse_test_table(table_markdown))

    async def parse_test_table(self, table_markdown: str) -> list[TestScenario]:
        if self.llm is None:
            return []
        prompt = (
            f"{LLM_JSON_ONLY_RULE} "
            "Shape: {\"scenarios\":[{code,title,requirement_ref,description,cases}]}. "
            "Each case: {code,title,priority,test_type,preconditions,steps}. "
            "Each step: {step_no,action,intent_hint,target,input_data,expected_result}. "
            "Actions must be one of: navigate,click,fill,select,hover,scroll,wait,"
            "assert,api_request (also accept goto/assert_visible aliases). "
            "preconditions may be a string or string[]; never character-split. "
            "Put every input value / threshold into input_data (test_data).\n\n"
            + self.compressor.budget.truncate_to_cap(table_markdown, cap=4000)
        )
        raw = await self.llm(prompt)
        return self._parse_artifact_bundle(raw)

    async def generate_outline(self, markdown_spec: str) -> list[TestScenario]:
        prompt = (
            f"{LLM_JSON_ONLY_RULE} "
            "Shape: a JSON array of scenarios, or {\"scenarios\":[...]}. "
            "Each item keys: code,title,requirement_ref,description. "
            f"{SPEC_OUTLINE_COVER_RULE}\n\n"
            + self.compressor.budget.truncate_to_cap(markdown_spec, cap=3000)
        )
        if self.llm is None:
            return self._offline_outline(markdown_spec)
        raw = await self.llm(prompt)
        return self._parse_scenarios(raw)

    async def generate_cases_batch(
        self, scenario: TestScenario, batch_size: int = 3
    ) -> list[TestCase]:
        if self.llm is None:
            return self._offline_cases(scenario, batch_size)
        prompt = (
            f"{LLM_JSON_ONLY_RULE} "
            f"Generate up to {batch_size} IEEE-style test cases as JSON for scenario "
            f"{scenario.code}: {scenario.title}. "
            "Shape: a JSON array of cases, or {\"cases\":[...]}. "
            "Each case keys: code,title,priority,test_type,preconditions,steps. "
            "Use priority P1/P2/P3 appropriately (not all P1). "
            "Actions: navigate|click|fill|select|hover|scroll|wait|assert|api_request. "
            "Each step must include intent_hint, target (semantic UI object), and "
            "input_data whenever values/thresholds appear."
        )
        raw = await self.llm(prompt)
        return self._parse_cases(raw, scenario)

    def _offline_outline(self, markdown_spec: str) -> list[TestScenario]:
        title = markdown_spec.strip().splitlines()[0][:80] if markdown_spec.strip() else "Scenario"
        return [
            TestScenario(
                id=str(uuid.uuid4()),
                code="TS_HAPPY_01",
                title=f"Happy path — {title}",
                requirement_ref="REQ-1",
                description="Primary success flow",
            ),
            TestScenario(
                id=str(uuid.uuid4()),
                code="TS_NEG_01",
                title=f"Negative — {title}",
                requirement_ref="REQ-1",
                description="Invalid input / auth failure",
            ),
        ]

    def _offline_cases(self, scenario: TestScenario, batch_size: int) -> list[TestCase]:
        cases: list[TestCase] = []
        for i in range(max(1, min(batch_size, 3))):
            cases.append(
                TestCase(
                    id=str(uuid.uuid4()),
                    code=f"{scenario.code}_C{i+1:02d}",
                    title=f"{scenario.title} case {i+1}",
                    requirement_ref=scenario.requirement_ref,
                    steps=[
                        TestStep(
                            step_no=1,
                            action="goto",
                            intent_hint="Open target application",
                            input_data="/",
                            binding_status="UNBOUND",
                        )
                    ],
                )
            )
        return cases

    @staticmethod
    def _scenario_items(data: Any) -> list[Any]:
        if isinstance(data, list):
            return data
        if isinstance(data, dict):
            items = data.get("scenarios")
            if isinstance(items, list):
                return items
            # Single scenario object mistaken for a wrapper.
            if any(key in data for key in ("code", "title", "description")):
                return [data]
        return []

    @staticmethod
    def _case_items(data: Any) -> list[Any]:
        if isinstance(data, list):
            return data
        if isinstance(data, dict):
            items = data.get("cases")
            if isinstance(items, list):
                return items
            if any(key in data for key in ("code", "title", "steps")):
                return [data]
        return []

    def _parse_scenarios(self, raw: str) -> list[TestScenario]:
        """Parse LLM outline JSON. Empty on failure — never treat the raw reply as SRS."""
        data = loads_llm_json(raw)
        if data is None:
            logger.warning(
                "outline_json_failed preview=%s",
                preview_llm_text(raw),
            )
            return []
        items = self._scenario_items(data)
        scenarios: list[TestScenario] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            scenarios.append(
                TestScenario(
                    id=str(uuid.uuid4()),
                    code=str(item.get("code") or f"TS_{len(scenarios)+1}"),
                    title=str(item.get("title") or "Scenario"),
                    requirement_ref=str(item.get("requirement_ref") or ""),
                    description=str(item.get("description") or ""),
                )
            )
        if not scenarios:
            logger.warning(
                "outline_json_empty_shape preview=%s",
                preview_llm_text(raw),
            )
            return []
        return scenarios

    def _parse_artifact_bundle(self, raw: str) -> list[TestScenario]:
        data = loads_llm_json(raw)
        if data is None:
            logger.warning(
                "artifact_json_failed preview=%s",
                preview_llm_text(raw),
            )
            return []
        items = self._scenario_items(data)
        scenarios: list[TestScenario] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            scenario = TestScenario(
                id=str(uuid.uuid4()),
                code=str(item.get("code") or f"TS_{len(scenarios) + 1}"),
                title=str(item.get("title") or "Scenario"),
                requirement_ref=str(item.get("requirement_ref") or ""),
                description=str(item.get("description") or ""),
                cases=[],
            )
            cases_raw = item.get("cases") or []
            if isinstance(cases_raw, list):
                for case_item in cases_raw:
                    if not isinstance(case_item, dict):
                        continue
                    steps = self._parse_steps(case_item.get("steps") or [])
                    scenario.cases.append(
                        TestCase(
                            id=str(uuid.uuid4()),
                            code=str(
                                case_item.get("code")
                                or f"{scenario.code}_C{len(scenario.cases) + 1:02d}"
                            ),
                            title=str(case_item.get("title") or scenario.title),
                            priority=self._parse_priority(case_item.get("priority")),
                            test_type=self._parse_test_type(case_item.get("test_type")),
                            preconditions=coerce_preconditions(case_item.get("preconditions")),
                            steps=steps,
                            requirement_ref=scenario.requirement_ref,
                            description=str(case_item.get("description") or ""),
                            expected_final_result=str(
                                case_item.get("expected_final_result")
                                or case_item.get("expected_result")
                                or ""
                            ),
                        )
                    )
                    ensure_case_test_data_rows(scenario.cases[-1])
            scenarios.append(scenario)
        return scenarios

    def _parse_cases(self, raw: str, scenario: TestScenario) -> list[TestCase]:
        """Parse LLM case JSON. Empty on failure — caller may fall back to offline cases."""
        data = loads_llm_json(raw)
        if data is None:
            logger.warning(
                "cases_json_failed preview=%s",
                preview_llm_text(raw),
            )
            return []
        items = self._case_items(data)
        cases: list[TestCase] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            steps = self._parse_steps(item.get("steps") or [])
            case = TestCase(
                id=str(uuid.uuid4()),
                code=str(item.get("code") or f"{scenario.code}_C{len(cases)+1:02d}"),
                title=str(item.get("title") or scenario.title),
                priority=self._parse_priority(item.get("priority")),
                test_type=self._parse_test_type(item.get("test_type")),
                preconditions=coerce_preconditions(item.get("preconditions")),
                steps=steps,
                requirement_ref=scenario.requirement_ref,
                description=str(item.get("description") or ""),
                expected_final_result=str(
                    item.get("expected_final_result") or item.get("expected_result") or ""
                ),
            )
            cases.append(ensure_case_test_data_rows(case))
        return cases

    @staticmethod
    def _parse_priority(raw: object) -> Priority:
        text = str(raw or "").strip()
        if not text:
            return "P1 - High"
        if text in {"P0 - Critical", "P1 - High", "P2 - Medium", "P3 - Low"}:
            return text  # type: ignore[return-value]
        mapped = PRIORITY_MAP.get(text.upper()[:2])
        if mapped is not None:
            return mapped  # type: ignore[return-value]
        lowered = text.lower()
        if "critical" in lowered or "p0" in lowered:
            return "P0 - Critical"
        if "high" in lowered or "p1" in lowered:
            return "P1 - High"
        if "medium" in lowered or "p2" in lowered:
            return "P2 - Medium"
        if "low" in lowered or "p3" in lowered:
            return "P3 - Low"
        return "P1 - High"

    @staticmethod
    def _parse_test_type(raw: object) -> TestType:
        text = str(raw or "").strip()
        allowed = {
            "Functional",
            "Negative",
            "Boundary",
            "UI/UX",
            "Security",
            "API",
        }
        if text in allowed:
            return text  # type: ignore[return-value]
        lowered = text.lower()
        if "api" in lowered:
            return "API"
        if "neg" in lowered:
            return "Negative"
        if "bound" in lowered or "bva" in lowered:
            return "Boundary"
        if "ui" in lowered or "ux" in lowered:
            return "UI/UX"
        if "sec" in lowered:
            return "Security"
        return "Functional"

    @staticmethod
    def _parse_steps(steps_raw: object) -> list[TestStep]:
        if not isinstance(steps_raw, list):
            return []
        steps: list[TestStep] = []
        for idx, raw_step in enumerate(steps_raw, start=1):
            if not isinstance(raw_step, dict):
                continue
            steps.append(normalize_raw_step(raw_step, index=idx))
        return steps
