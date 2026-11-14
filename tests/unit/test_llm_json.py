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


def test_strip_fence_and_preview() -> None:
    assert strip_markdown_fence("```\n[1]\n```") == "[1]"
    assert "…" in preview_llm_text("word " * 80, limit=40)
