"""Lean DOM snapshot → DistilledNode tree (no aria dump by default)."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.constants import (
    SNAPSHOT_INCLUDE_ARIA_DEFAULT,
    SNAPSHOT_MAX_NODES,
)
from ai_qa_test_studio.core.models import DistilledNode, ElementFingerprint
from ai_qa_test_studio.core.token_budget import TokenBudget

_EXTRACT_JS_TEMPLATE = """
() => {
  const maxNodes = __MAX_NODES__;
  const nodes = Array.from(document.querySelectorAll(
    'a,button,input,select,textarea,[role="button"],[contenteditable="true"]'
  )).filter(el => {
    const style = window.getComputedStyle(el);
    return style && style.visibility !== 'hidden' && style.display !== 'none';
  });
  return nodes.slice(0, maxNodes).map((el, i) => {
    const classPart = String(el.className || '').trim().split(/\\s+/).slice(0, 2).join('.');
    const css = el.id
      ? ('#' + el.id)
      : (classPart ? (el.tagName.toLowerCase() + '.' + classPart) : el.tagName.toLowerCase());
    const label = el.innerText
      || el.getAttribute('aria-label')
      || el.getAttribute('name')
      || '';
    let xpath = '';
    try {
      const parts = [];
      let cur = el;
      for (let depth = 0; cur && cur.nodeType === 1 && depth < 6; depth++) {
        let ix = 1;
        let sib = cur.previousElementSibling;
        while (sib) {
          if (sib.tagName === cur.tagName) ix += 1;
          sib = sib.previousElementSibling;
        }
        parts.unshift(cur.tagName.toLowerCase() + '[' + ix + ']');
        cur = cur.parentElement;
      }
      xpath = '/' + parts.join('/');
    } catch (e) {
      xpath = '';
    }
    return {
      id: 'e' + (i + 1),
      siblingIndex: i,
      tag: el.tagName.toLowerCase(),
      role: el.getAttribute('role') || '',
      name: label.trim().slice(0, 80),
      placeholder: el.getAttribute('placeholder') || '',
      testId: el.getAttribute('data-testid') || '',
      css,
      xpath,
    };
  });
}
"""


def _extract_js(max_nodes: int) -> str:
    return _EXTRACT_JS_TEMPLATE.replace("__MAX_NODES__", str(int(max_nodes)))


class SnapshotDistiller:
    """Emit DistilledNode list; aria snapshot is opt-in only."""

    def __init__(
        self,
        budget: TokenBudget | None = None,
        *,
        max_nodes: int = SNAPSHOT_MAX_NODES,
        include_aria: bool = SNAPSHOT_INCLUDE_ARIA_DEFAULT,
    ) -> None:
        self.budget = budget or TokenBudget()
        self.max_nodes = max_nodes
        self.include_aria = include_aria

    async def distill(self, page: Any) -> list[DistilledNode]:
        raw = await page.evaluate(_extract_js(self.max_nodes))
        if not isinstance(raw, list):
            return []
        nodes: list[DistilledNode] = []
        for item in raw:
            if not isinstance(item, dict):
                continue
            short_id = str(item.get("id") or "")
            if not short_id:
                continue
            name = str(item.get("name") or "")
            fp = ElementFingerprint(
                tag=str(item.get("tag") or ""),
                role=str(item.get("role") or "") or None,
                accessible_name=name or None,
                text_sample=name or None,
                test_id=str(item.get("testId") or "") or None,
                css=str(item.get("css") or "") or None,
                xpath=str(item.get("xpath") or "") or None,
                attrs_subset={
                    k: str(item[k])
                    for k in ("placeholder",)
                    if item.get(k)
                },
            )
            nodes.append(
                DistilledNode(
                    short_id=short_id,
                    parent_short_id=None,
                    sibling_index=int(item.get("siblingIndex") or 0),
                    fingerprint=fp,
                )
            )
        return nodes

    async def extract_legacy(
        self, page: Any
    ) -> tuple[str, dict[str, dict[str, Any]]]:
        """Backward-compatible compact string + element_map (no aria by default)."""
        nodes = await self.distill(page)
        element_map: dict[str, dict[str, Any]] = {}
        lines: list[str] = []
        for node in nodes:
            fp = node.fingerprint
            meta = {
                "id": node.short_id,
                "tag": fp.tag,
                "role": fp.role or "",
                "name": fp.accessible_name or "",
                "placeholder": fp.attrs_subset.get("placeholder", ""),
                "testId": fp.test_id or "",
                "css": fp.css or "",
                "xpath": fp.xpath or "",
            }
            element_map[node.short_id] = meta
            lines.append(
                f"[{node.short_id}] {fp.tag} name={fp.accessible_name!r} css={fp.css}"
            )
        compact = "\n".join(lines)
        if self.include_aria:
            try:
                snapshot = await page.locator("body").aria_snapshot()
                if not isinstance(snapshot, str):
                    snapshot = str(snapshot)
                compact = compact + "\n\n" + snapshot
            except Exception:  # noqa: BLE001
                pass
        return self.budget.truncate_to_cap(compact), element_map
