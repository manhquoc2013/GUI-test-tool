"""Outline-first scenario generation + chunked test-case batches."""

from __future__ import annotations

import json
import logging
import uuid
from collections.abc import Awaitable, Callable

from ai_qa_test_studio.core.models import TestCase, TestScenario, TestStep
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

    async def generate_outline(self, markdown_spec: str) -> list[TestScenario]:
        prompt = (
            "Return JSON list of scenarios with keys code,title,requirement_ref,description. "
            "Happy/Negative/Edge only.\n\n"
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
            f"Generate up to {batch_size} IEEE-style test cases as JSON for scenario "
            f"{scenario.code}: {scenario.title}. Keys: code,title,priority,preconditions,steps."
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

    def _parse_scenarios(self, raw: str) -> list[TestScenario]:
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("outline_json_failed")
            return self._offline_outline(raw)
        items = data if isinstance(data, list) else data.get("scenarios", [])
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
        return scenarios or self._offline_outline(raw)

    def _parse_cases(self, raw: str, scenario: TestScenario) -> list[TestCase]:
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return self._offline_cases(scenario, 2)
        items = data if isinstance(data, list) else data.get("cases", [])
        cases: list[TestCase] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            steps_raw = item.get("steps") or []
            steps: list[TestStep] = []
            for idx, s in enumerate(steps_raw, start=1):
                if not isinstance(s, dict):
                    continue
                steps.append(
                    TestStep(
                        step_no=int(s.get("step_no") or idx),
                        action=s.get("action") or "click",
                        intent_hint=str(s.get("intent_hint") or s.get("description") or ""),
                        input_data=s.get("input_data"),
                        expected_result=str(s.get("expected_result") or ""),
                        binding_status="UNBOUND",
                    )
                )
            cases.append(
                TestCase(
                    id=str(uuid.uuid4()),
                    code=str(item.get("code") or f"{scenario.code}_C{len(cases)+1:02d}"),
                    title=str(item.get("title") or scenario.title),
                    preconditions=[str(p) for p in (item.get("preconditions") or [])],
                    steps=steps,
                    requirement_ref=scenario.requirement_ref,
                )
            )
        return cases or self._offline_cases(scenario, 2)
