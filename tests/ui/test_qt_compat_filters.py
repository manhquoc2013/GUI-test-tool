from __future__ import annotations

import io

from ai_qa_test_studio.ui.qt_compat import _LineFilteringStream


def test_stderr_filter_drops_invalid_object_noise() -> None:
    sink = io.StringIO()
    filtered = _LineFilteringStream(sink)
    filtered.write("Called attribute on invalid object:  2147483690\n")
    filtered.write("Called accessibilityLabel on invalid object:  2147483690\n")
    filtered.write("INFO real_log keep_me\n")
    filtered.flush()
    assert "invalid object" not in sink.getvalue()
    assert "keep_me" in sink.getvalue()
