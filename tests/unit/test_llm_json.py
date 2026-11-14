from __future__ import annotations

from ai_qa_test_studio.agent.llm_json import loads_llm_json, preview_llm_text, strip_markdown_fence


def test_loads_plain_array() -> None:
    data = loads_llm_json('[{"code":"TS1","title":"A"}]')
    assert isinstance(data, list)
    assert data[0]["code"] == "TS1"


def test_loads_fenced_json_object() -> None:
    raw = """Here is the outline:

```json
{
  "scenarios": [
    {"code": "TS_HAPPY_01", "title": "Login ok"},
  ]
}
```

Hope this helps!
"""
    data = loads_llm_json(raw)
    assert isinstance(data, dict)
    assert data["scenarios"][0]["title"] == "Login ok"


def test_loads_prose_wrapped_array() -> None:
    raw = 'Sure:\n[{"code":"TS_NEG_01","title":"Bad password"}]\nDone.'
    data = loads_llm_json(raw)
    assert isinstance(data, list)
    assert data[0]["code"] == "TS_NEG_01"


def test_loads_rejects_garbage() -> None:
    assert loads_llm_json("not json at all") is None
    assert loads_llm_json("") is None
    assert loads_llm_json("```json") is None


def test_loads_unclosed_fence_array() -> None:
    raw = '```json\n[{"code":"TS1","title":"A"}]\n'
    data = loads_llm_json(raw)
    assert isinstance(data, list)
    assert data[0]["code"] == "TS1"


def test_loads_truncated_array_recovers_closed_prefix_object() -> None:
    """Max-token cut mid-array: close the open string/brackets and keep complete items."""
    raw = '```json\n[{"code":"TS1","title":"A"}'
    data = loads_llm_json(raw)
    assert isinstance(data, list)
    assert data[0]["code"] == "TS1"
    assert data[0]["title"] == "A"


def test_loads_truncated_scenarios_keeps_complete_items() -> None:
    raw = (
        '{ "scenarios": [ '
        '{ "code": "S01-MF-01", "title": "Happy", "requirement_ref": "SRS 1.2", '
        '"description": "Quản trị viên tạo hồ sơ thành công" }, '
        '{ "code": "S01-MF-02", "title": "Negat'
    )
    data = loads_llm_json(raw)
    assert isinstance(data, dict)
    scenarios = data["scenarios"]
    assert len(scenarios) >= 1
    assert scenarios[0]["code"] == "S01-MF-01"
    assert "hồ sơ" in scenarios[0]["description"]


def test_loads_fancy_quotes() -> None:
    raw = '{“scenarios”:[{“code”:“TS1”,“title”:“A”}]}'
    data = loads_llm_json(raw)
    assert isinstance(data, dict)
    assert data["scenarios"][0]["code"] == "TS1"


def test_loads_unescaped_newline_in_string() -> None:
    raw = (
        '{ "scenarios": [ { "code": "S01-MF-01", "title": "Happy", '
        '"description": "Quản trị viên truy cập\nmàn hình hồ sơ'  # cut mid-string + raw NL
    )
    data = loads_llm_json(raw)
    assert isinstance(data, dict)
    assert data["scenarios"][0]["code"] == "S01-MF-01"
    assert "Quản trị viên" in data["scenarios"][0]["description"]


def test_loads_recovers_complete_items_when_second_object_broken() -> None:
    raw = (
        '{ "scenarios": [ '
        '{ "code": "S01-MF-01", "title": "Happy", "requirement_ref": "SRS 1.2", '
        '"description": "ok" }, '
        '{ "code": "S01-MF-02", "title": "Neg\nbroken'
    )
    data = loads_llm_json(raw)
    assert isinstance(data, dict)
    codes = [item["code"] for item in data["scenarios"]]
    assert "S01-MF-01" in codes


def test_loads_dangling_backslash_in_string() -> None:
    raw = '{ "scenarios": [ { "code": "S01", "title": "A\\'
    data = loads_llm_json(raw)
    assert isinstance(data, dict)
    assert data["scenarios"][0]["title"] == "A"


def test_strip_fence_and_preview() -> None:
    assert strip_markdown_fence("```\n[1]\n```") == "[1]"
    assert strip_markdown_fence("```json\n[1]") == "[1]"
    assert "…" in preview_llm_text("word " * 80, limit=40)
