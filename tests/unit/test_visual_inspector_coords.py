from __future__ import annotations

from ai_qa_test_studio.browser.visual_inspector import ViewportMapping, widget_to_css_point


def test_hidpi_letterbox_mapping() -> None:
    mapping = ViewportMapping(
        offset_x=100,
        offset_y=50,
        rendered_width=800,
        rendered_height=450,
        viewport_width=1600,
        viewport_height=900,
    )
    css_x, css_y = widget_to_css_point(100, 50, mapping)
    assert css_x == 0
    assert css_y == 0
    css_x2, css_y2 = widget_to_css_point(500, 275, mapping)
    assert css_x2 == 800
    assert css_y2 == 450
