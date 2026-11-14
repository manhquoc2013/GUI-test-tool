"""Lean DOM snapshot → DistilledNode tree (Shadow DOM + nested iframes)."""

from __future__ import annotations

import logging
from typing import Any

from ai_qa_test_studio.constants import (
    ATTR_IN_SHADOW_DOM,
    ATTR_SHADOW_HOST_CHAIN,
    FRAME_PATH_NTH_SEP,
    SNAPSHOT_IFRAME_MIN_SIZE_PX,
    SNAPSHOT_INCLUDE_ARIA_DEFAULT,
    SNAPSHOT_MAX_FRAME_DEPTH,
    SNAPSHOT_MAX_NODES,
    UI_VIEWPORT_HEIGHT,
    UI_VIEWPORT_WIDTH,
)
from ai_qa_test_studio.core.models import DistilledNode, ElementFingerprint
from ai_qa_test_studio.core.token_budget import TokenBudget

logger = logging.getLogger(__name__)

_EXTRACT_JS_TEMPLATE = """
() => {
  const maxNodes = __MAX_NODES__;
  const SELECTOR = [
    'a', 'button', 'input', 'select', 'textarea',
    '[role]', '[data-testid]', '[contenteditable="true"]'
  ].join(',');
  const out = [];
  const seen = new WeakSet();

  function isVisible(el) {
    try {
      const style = window.getComputedStyle(el);
      if (!style || style.visibility === 'hidden' || style.display === 'none') return false;
      const rect = el.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0;
    } catch (e) {
      return false;
    }
  }

  function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === 'function') {
      return window.CSS.escape(value);
    }
    return String(value).replace(/([ !"#$%&'()*+,./:;<=>?@[\\\\]^`{|}~])/g, '\\\\$1');
  }

  function cssPath(el) {
    if (!el || el.nodeType !== 1) return '';
    if (el.id) return '#' + cssEscape(el.id);
    const testId = el.getAttribute('data-testid');
    if (testId) return '[data-testid="' + String(testId).replace(/"/g, '\\\\"') + '"]';
    const tag = el.tagName.toLowerCase();
    const classPart = String(el.className || '')
      .trim()
      .split(/\\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(cssEscape)
      .join('.');
    return classPart ? (tag + '.' + classPart) : tag;
  }

  function xpathFor(el) {
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
        const root = cur.getRootNode && cur.getRootNode();
        if (root && root instanceof ShadowRoot) {
          break;
        }
        cur = cur.parentElement;
      }
      return '/' + parts.join('/');
    } catch (e) {
      return '';
    }
  }

  function pierceCss(el, shadowHosts) {
    const leaf = cssPath(el);
    if (!shadowHosts.length) return leaf;
    return shadowHosts.map(cssPath).filter(Boolean).concat(leaf).join(' >> ');
  }

  function pushNode(el, shadowHosts) {
    if (seen.has(el) || !isVisible(el) || out.length >= maxNodes) return;
    seen.add(el);
    const rect = el.getBoundingClientRect();
    const label = el.innerText
      || el.getAttribute('aria-label')
      || el.getAttribute('name')
      || '';
    const hostChain = shadowHosts.map(cssPath).filter(Boolean);
    out.push({
      tag: el.tagName.toLowerCase(),
      role: el.getAttribute('role') || '',
      name: String(label).trim().slice(0, 80),
      placeholder: el.getAttribute('placeholder') || '',
      testId: el.getAttribute('data-testid') || '',
      css: pierceCss(el, shadowHosts),
      xpath: xpathFor(el),
      inShadowDom: shadowHosts.length > 0,
      shadowHostChain: hostChain,
      bbox: { x: rect.x, y: rect.y, w: rect.width, h: rect.height },
    });
  }

  function collect(root, shadowHosts) {
    if (!root || out.length >= maxNodes) return;
    let els;
    try {
      els = Array.from(root.querySelectorAll(SELECTOR));
    } catch (e) {
      return;
    }
    for (const el of els) {
      pushNode(el, shadowHosts);
      if (out.length >= maxNodes) return;
    }
    let all;
    try {
      all = Array.from(root.querySelectorAll('*'));
    } catch (e) {
      return;
    }
    for (const host of all) {
      if (host.shadowRoot) {
        collect(host.shadowRoot, shadowHosts.concat([host]));
        if (out.length >= maxNodes) return;
      }
    }
  }

  collect(document, []);
  return out.slice(0, maxNodes);
}
"""

_IFRAME_META_JS = """
(el) => {
  const style = window.getComputedStyle(el);
  const rect = el.getBoundingClientRect();
  const minSize = __MIN_SIZE__;
  const hidden = !style
    || style.display === 'none'
    || style.visibility === 'hidden'
    || rect.width < minSize
    || rect.height < minSize;
  const src = el.getAttribute('src') || '';
  const tracking = /doubleclick|googletag|adservice|facebook\\.com\\/tr|hotjar|analytics|googlesyndication/i.test(src);
  function esc(v) {
    if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(v);
    return String(v).replace(/"/g, '\\\\"');
  }
  let sel = '';
  if (el.id) sel = 'iframe#' + esc(el.id);
  else if (el.getAttribute('name')) {
    sel = 'iframe[name="' + String(el.getAttribute('name')).replace(/"/g, '\\\\"') + '"]';
  } else if (el.getAttribute('title')) {
    sel = 'iframe[title="' + String(el.getAttribute('title')).replace(/"/g, '\\\\"') + '"]';
  } else if (src) {
    const frag = src.split('/').filter(Boolean).pop() || '';
    const short = frag.slice(0, 48).replace(/"/g, '');
    if (short) sel = 'iframe[src*="' + short + '"]';
  }
  return {
    hidden: !!hidden,
    tracking: !!tracking,
    sel,
    x: rect.x,
    y: rect.y,
  };
}
"""


def _extract_js(max_nodes: int) -> str:
    return _EXTRACT_JS_TEMPLATE.replace("__MAX_NODES__", str(int(max_nodes)))


def _iframe_meta_js() -> str:
    return _IFRAME_META_JS.replace("__MIN_SIZE__", str(int(SNAPSHOT_IFRAME_MIN_SIZE_PX)))


class SnapshotDistiller:
    """Emit DistilledNode list; aria snapshot is opt-in only."""

    def __init__(
        self,
        budget: TokenBudget | None = None,
        *,
        max_nodes: int = SNAPSHOT_MAX_NODES,
        include_aria: bool = SNAPSHOT_INCLUDE_ARIA_DEFAULT,
        max_frame_depth: int = SNAPSHOT_MAX_FRAME_DEPTH,
    ) -> None:
        self.budget = budget or TokenBudget()
        self.max_nodes = max_nodes
        self.include_aria = include_aria
        self.max_frame_depth = max_frame_depth

    async def distill(self, page: Any) -> list[DistilledNode]:
        viewport = getattr(page, "viewport_size", None) or {
            "width": UI_VIEWPORT_WIDTH,
            "height": UI_VIEWPORT_HEIGHT,
        }
        vw = max(float(viewport.get("width") or UI_VIEWPORT_WIDTH), 1.0)
        vh = max(float(viewport.get("height") or UI_VIEWPORT_HEIGHT), 1.0)
        nodes: list[DistilledNode] = []
        # page.evaluate runs in the main frame (keeps unit mocks simple).
        counter = await self._ingest_raw(
            evaluate_target=page,
            frame_path=[],
            offset_x=0.0,
            offset_y=0.0,
            nodes=nodes,
            counter=0,
            vw=vw,
            vh=vh,
        )
        main = getattr(page, "main_frame", None)
        for nth, child in enumerate(self._child_frames(main)):
            if len(nodes) >= self.max_nodes:
                break
            sel, dx, dy = await self._iframe_identity(child, nth)
            if sel is None:
                continue
            counter = await self._distill_frame(
                frame=child,
                frame_path=[sel],
                offset_x=dx,
                offset_y=dy,
                depth=1,
                nodes=nodes,
                counter=counter,
                vw=vw,
                vh=vh,
            )
        return nodes

    async def _distill_frame(
        self,
        *,
        frame: Any,
        frame_path: list[str],
        offset_x: float,
        offset_y: float,
        depth: int,
        nodes: list[DistilledNode],
        counter: int,
        vw: float,
        vh: float,
    ) -> int:
        if depth > self.max_frame_depth or len(nodes) >= self.max_nodes:
            return counter
        counter = await self._ingest_raw(
            evaluate_target=frame,
            frame_path=frame_path,
            offset_x=offset_x,
            offset_y=offset_y,
            nodes=nodes,
            counter=counter,
            vw=vw,
            vh=vh,
        )
        for nth, child in enumerate(self._child_frames(frame)):
            if len(nodes) >= self.max_nodes or depth + 1 > self.max_frame_depth:
                break
            sel, dx, dy = await self._iframe_identity(child, nth)
            if sel is None:
                continue
            counter = await self._distill_frame(
                frame=child,
                frame_path=[*frame_path, sel],
                offset_x=offset_x + dx,
                offset_y=offset_y + dy,
                depth=depth + 1,
                nodes=nodes,
                counter=counter,
                vw=vw,
                vh=vh,
            )
        return counter

    @staticmethod
    def _child_frames(frame: Any) -> list[Any]:
        raw = getattr(frame, "child_frames", None) if frame is not None else None
        if isinstance(raw, (list, tuple)):
            return list(raw)
        return []

    async def _ingest_raw(
        self,
        *,
        evaluate_target: Any,
        frame_path: list[str],
        offset_x: float,
        offset_y: float,
        nodes: list[DistilledNode],
        counter: int,
        vw: float,
        vh: float,
    ) -> int:
        remaining = self.max_nodes - len(nodes)
        if remaining <= 0:
            return counter
        try:
            raw = await evaluate_target.evaluate(_extract_js(remaining))
        except Exception as exc:  # noqa: BLE001
            logger.debug("snapshot_frame_skip path=%s err=%s", frame_path, exc)
            return counter
        if not isinstance(raw, list):
            return counter
        for item in raw:
            if not isinstance(item, dict) or len(nodes) >= self.max_nodes:
                continue
            counter += 1
            short_id = f"e{counter}"
            name = str(item.get("name") or "")
            attrs: dict[str, str] = {}
            placeholder = item.get("placeholder")
            if placeholder:
                attrs["placeholder"] = str(placeholder)
            in_shadow = bool(item.get("inShadowDom"))
            if in_shadow:
                attrs[ATTR_IN_SHADOW_DOM] = "true"
                chain = item.get("shadowHostChain")
                if isinstance(chain, list) and chain:
                    attrs[ATTR_SHADOW_HOST_CHAIN] = " >> ".join(str(p) for p in chain if p)
            fp = ElementFingerprint(
                tag=str(item.get("tag") or ""),
                role=str(item.get("role") or "") or None,
                accessible_name=name or None,
                text_sample=name or None,
                test_id=str(item.get("testId") or "") or None,
                css=str(item.get("css") or "") or None,
                xpath=str(item.get("xpath") or "") or None,
                frame_path=list(frame_path),
                attrs_subset=attrs,
            )
            bbox_norm = self._bbox_norm(item.get("bbox"), offset_x, offset_y, vw, vh)
            nodes.append(
                DistilledNode(
                    short_id=short_id,
                    parent_short_id=None,
                    sibling_index=len(nodes),
                    bbox_norm=bbox_norm,
                    fingerprint=fp,
                )
            )
        return counter

    @staticmethod
    def _bbox_norm(
        bbox: object,
        offset_x: float,
        offset_y: float,
        vw: float,
        vh: float,
    ) -> tuple[float, float, float, float] | None:
        if not isinstance(bbox, dict):
            return None
        try:
            x = (offset_x + float(bbox.get("x") or 0.0)) / vw
            y = (offset_y + float(bbox.get("y") or 0.0)) / vh
            w = float(bbox.get("w") or 0.0) / vw
            h = float(bbox.get("h") or 0.0) / vh
        except (TypeError, ValueError):
            return None
        return (x, y, w, h)

    async def _iframe_identity(
        self,
        child: Any,
        nth: int,
    ) -> tuple[str | None, float, float]:
        try:
            element = await child.frame_element()
        except Exception:  # noqa: BLE001
            return None, 0.0, 0.0
        try:
            meta = await element.evaluate(_iframe_meta_js())
        except Exception:  # noqa: BLE001
            return None, 0.0, 0.0
        if not isinstance(meta, dict):
            return None, 0.0, 0.0
        if meta.get("hidden") or meta.get("tracking"):
            return None, 0.0, 0.0
        sel = str(meta.get("sel") or "").strip()
        if not sel:
            sel = f"iframe {FRAME_PATH_NTH_SEP}{nth}"
        try:
            dx = float(meta.get("x") or 0.0)
            dy = float(meta.get("y") or 0.0)
        except (TypeError, ValueError):
            dx, dy = 0.0, 0.0
        return sel, dx, dy

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
                "framePath": list(fp.frame_path),
                "inShadowDom": fp.attrs_subset.get(ATTR_IN_SHADOW_DOM, ""),
            }
            element_map[node.short_id] = meta
            frame_hint = f" frame={fp.frame_path!r}" if fp.frame_path else ""
            lines.append(
                f"[{node.short_id}] {fp.tag} name={fp.accessible_name!r} "
                f"css={fp.css}{frame_hint}"
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
