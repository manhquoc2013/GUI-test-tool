"""Distill interactive accessibility tree into compact [e1]..[en] indexes."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.core.token_budget import TokenBudget

_EXTRACT_JS = """
() => {
  const nodes = Array.from(document.querySelectorAll(
    'a,button,input,select,textarea,[role="button"],[contenteditable="true"]'
  )).filter(el => {
    const style = window.getComputedStyle(el);
    return style && style.visibility !== 'hidden' && style.display !== 'none';
  });
  return nodes.slice(0, 80).map((el, i) => {
    const classPart = String(el.className || '').trim().split(/\\s+/).slice(0, 2).join('.');
    const css = el.id
      ? ('#' + el.id)
      : (classPart ? (el.tagName.toLowerCase() + '.' + classPart) : el.tagName.toLowerCase());
    const label = el.innerText
      || el.getAttribute('aria-label')
      || el.getAttribute('name')
      || '';
    return {
      id: 'e' + (i + 1),
      tag: el.tagName.toLowerCase(),
      role: el.getAttribute('role') || '',
      name: label.trim().slice(0, 80),
      placeholder: el.getAttribute('placeholder') || '',
      testId: el.getAttribute('data-testid') || '',
      css,
    };
  });
}
"""


class DomDistiller:
    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()

    async def extract(self, page: Any) -> tuple[str, dict[str, dict[str, Any]]]:
        snapshot = await page.locator("body").aria_snapshot()
        if not isinstance(snapshot, str):
            snapshot = str(snapshot)
        elements = await page.evaluate(_EXTRACT_JS)
        element_map: dict[str, dict[str, Any]] = {item["id"]: item for item in elements}
        lines = [
            f"[{item['id']}] {item['tag']} name={item['name']!r} css={item['css']}"
            for item in elements
        ]
        compact = self.budget.truncate_to_cap("\n".join(lines) + "\n\n" + snapshot)
        return compact, element_map
