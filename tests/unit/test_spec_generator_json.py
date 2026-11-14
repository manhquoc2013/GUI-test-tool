from __future__ import annotations

from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.core.models import TestScenario


def test_parse_scenarios_from_markdown_fence() -> None:
    gen = SpecGenerator()
    raw = """```json
[
  {"code": "TS_HAPPY_01", "title": "Happy login", "requirement_ref": "REQ-1", "description": "ok"},
  {"code": "TS_NEG_01", "title": "Bad login", "requirement_ref": "REQ-1", "description": "fail"}
]
```"""
    scenarios = gen._parse_scenarios(raw)
    assert len(scenarios) == 2
    assert scenarios[0].code == "TS_HAPPY_01"
    assert scenarios[1].title == "Bad login"


def test_parse_scenarios_from_wrapped_object_with_trailing_comma() -> None:
    gen = SpecGenerator()
    raw = (
        "Outline:\n"
        '{"scenarios":[{"code":"TS_EDGE_01","title":"Empty field",},]}\n'
        "Thanks"
    )
    scenarios = gen._parse_scenarios(raw)
    assert len(scenarios) == 1
    assert scenarios[0].code == "TS_EDGE_01"


def test_parse_scenarios_falls_back_on_unusable_text() -> None:
    gen = SpecGenerator()
    scenarios = gen._parse_scenarios("I cannot produce JSON right now.")
    assert len(scenarios) == 2
    assert scenarios[0].code == "TS_HAPPY_01"
    assert scenarios[1].code == "TS_NEG_01"


def test_parse_cases_from_fenced_payload() -> None:
    gen = SpecGenerator()
    scenario = TestScenario(
        id="s1",
        code="TS_HAPPY_01",
        title="Happy",
        requirement_ref="REQ-1",
    )
    raw = """```json
{"cases":[{"code":"C01","title":"Case 1","steps":[{"step_no":1,"action":"goto","intent_hint":"open"}]}]}
```"""
    cases = gen._parse_cases(raw, scenario)
    assert len(cases) == 1
    assert cases[0].code == "C01"
    assert cases[0].steps[0].action == "goto"
