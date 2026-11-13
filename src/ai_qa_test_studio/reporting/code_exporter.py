"""Export pytest-playwright project + GitHub Actions workflow."""

from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.core.models import Project, SharedElement, TestCase


class CodeExporter:
    def export(self, project: Project, output_dir: Path) -> Path:
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / "tests").mkdir(exist_ok=True)
        (output_dir / ".github" / "workflows").mkdir(parents=True, exist_ok=True)
        (output_dir / "requirements.txt").write_text(
            "pytest\nplaywright\npytest-playwright\n", encoding="utf-8"
        )
        elements_path = output_dir / "shared_elements.py"
        elements_path.write_text(self._elements_module(project.shared_elements), encoding="utf-8")
        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                for case in scenario.cases:
                    self._write_case(output_dir / "tests", case)
        workflow = output_dir / ".github" / "workflows" / "qa-automation.yml"
        workflow.write_text(
            """name: qa-automation
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: pip install -r requirements.txt
      - run: playwright install --with-deps chromium
      - run: pytest -q
""",
            encoding="utf-8",
        )
        return output_dir

    def _elements_module(self, elements: list[SharedElement]) -> str:
        lines = ["ELEMENTS = {"]
        for el in elements:
            loc = el.primary_playwright or el.css or ""
            lines.append(f"    {el.element_id!r}: {loc!r},")
        lines.append("}")
        return "\n".join(lines) + "\n"

    def _write_case(self, tests_dir: Path, case: TestCase) -> None:
        safe = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in case.code)
        path = tests_dir / f"test_{safe}.py"
        body_lines = [
            "from playwright.sync_api import Page",
            "from shared_elements import ELEMENTS",
            "",
            f"def test_{safe}(page: Page) -> None:",
        ]
        if not case.steps:
            body_lines.append("    assert True  # placeholder — no steps")
        for step in case.steps:
            if step.action == "goto":
                url = step.input_data or case.start_url or "/"
                body_lines.append(f"    page.goto({url!r})")
            elif step.element_ref:
                body_lines.append(f"    locator = page.locator(ELEMENTS[{step.element_ref!r}])")
                if step.action == "click":
                    body_lines.append("    locator.click()")
                elif step.action == "fill":
                    body_lines.append(f"    locator.fill({(step.input_data or '')!r})")
                elif step.action == "assert_visible":
                    body_lines.append("    locator.wait_for(state='visible')")
        path.write_text("\n".join(body_lines) + "\n", encoding="utf-8")
