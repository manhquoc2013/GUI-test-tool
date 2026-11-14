"""Helpers to bind/unbind qconfig.themeChanged without pytest teardown crashes."""

from __future__ import annotations

from collections.abc import Callable

from qfluentwidgets import qconfig


def bind_theme_changed(slot: Callable[[], None]) -> None:
    qconfig.themeChanged.connect(slot)


def safe_theme_slot(fn: Callable[[], None]) -> Callable[[], None]:
    """Wrap a style applier so deleted C++ widgets do not raise during themeChanged."""

    def _wrapped() -> None:
        try:
            fn()
        except RuntimeError:
            try:
                qconfig.themeChanged.disconnect(_wrapped)
            except (TypeError, RuntimeError):
                return

    return _wrapped
