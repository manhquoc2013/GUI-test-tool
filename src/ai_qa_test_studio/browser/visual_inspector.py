"""CDP screencast helpers + HiDPI coordinate mapping for Inspector clicks."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ViewportMapping:
    offset_x: float
    offset_y: float
    rendered_width: float
    rendered_height: float
    viewport_width: float
    viewport_height: float


def widget_to_css_point(
    widget_x: float,
    widget_y: float,
    mapping: ViewportMapping,
) -> tuple[float, float]:
    """Map logical widget pixels to CSS viewport pixels (Retina / DPI scaling safe)."""
    if mapping.rendered_width <= 0 or mapping.rendered_height <= 0:
        raise ValueError("rendered dimensions must be positive")
    css_x = (widget_x - mapping.offset_x) * (mapping.viewport_width / mapping.rendered_width)
    css_y = (widget_y - mapping.offset_y) * (mapping.viewport_height / mapping.rendered_height)
    return css_x, css_y


async def element_from_point(page: object, css_x: float, css_y: float) -> dict[str, object] | None:
    result = await page.evaluate(  # type: ignore[attr-defined]
        """([x, y]) => {
          const el = document.elementFromPoint(x, y);
          if (!el) return null;
          return {
            tag: el.tagName.toLowerCase(),
            id: el.id || '',
            name: (el.getAttribute('aria-label') || el.innerText || '').trim().slice(0, 80),
            css: el.id ? ('#' + el.id) : el.tagName.toLowerCase(),
          };
        }""",
        [css_x, css_y],
    )
    if result is None:
        return None
    if not isinstance(result, dict):
        return None
    return {str(k): v for k, v in result.items()}
