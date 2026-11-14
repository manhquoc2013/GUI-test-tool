"""SpecGenerator step/precondition coercion from Copilot JSON."""

from __future__ import annotations

from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.core.models import TestScenario


def test_parse_cases_keeps_string_preconditions_and_infers_actions() -> None:
    gen = SpecGenerator(llm=None)
    scenario = TestScenario(id="s1", code="TS_01", title="Pricing")
    raw = """
    {
      "cases": [
        {
          "code": "TC_01",
          "title": "Kiểm tra bảng giá",
          "priority": "P2",
          "test_type": "Functional",
          "preconditions": "Trang pricing tải thành công",
          "steps": [
            {
              "step_no": 1,
              "action": "Navigate to pricing",
              "intent_hint": "Mở trang Pricing",
              "target": "Trang Pricing",
              "input_data": "https://example.com/pricing",
              "expected_result": "Trang hiển thị"
            },
            {
              "step_no": 2,
              "action": "scroll",
              "intent_hint": "Bảng giá PRICING",
              "expected_result": "Bảng trong viewport"
            },
            {
              "step_no": 3,
              "intent_hint": "Check giá deepseek-flash",
              "target": "Dòng cache hit - deepseek-flash",
              "test_data": "model=deepseek-flash; expected_price=$0.003",
              "expected_result": "Giá khớp"
            }
          ]
        }
      ]
    }
    """
    cases = gen._parse_cases(raw, scenario)
    assert len(cases) == 1
    case = cases[0]
    assert case.preconditions == ["Trang pricing tải thành công"]
    assert case.priority == "P2 - Medium"
    assert [s.action for s in case.steps] == ["navigate", "scroll", "assert"]
    assert case.steps[0].element_ref == "Trang Pricing"
    assert case.steps[2].element_ref == "Dòng cache hit - deepseek-flash"
    assert case.steps[2].input_data == "model=deepseek-flash; expected_price=$0.003"
