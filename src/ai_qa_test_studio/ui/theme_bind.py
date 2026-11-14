"""Helpers to bind/unbind qconfig.themeChanged without pytest teardown crashes."""

from __future__ import annotations

from collections.abc import Callable

from PySide6.QtCore import QObject
from qfluentwidgets import qconfig


def _disconnect_slot(slot: Callable[[], None]) -> None:
    try:
        qconfig.themeChanged.disconnect(slot)
    except (TypeError, RuntimeError):
        return


def safe_theme_slot(fn: Callable[[], None]) -> Callable[[], None]:
    """Wrap a style applier so deleted C++ widgets do not raise during themeChanged."""

    def _wrapped() -> None:
        try:
            fn()
        except RuntimeError:
            _disconnect_slot(_wrapped)

    return _wrapped


def bind_theme_changed(slot: Callable[[], None]) -> None:
    qconfig.themeChanged.connect(slot)


def bind_widget_theme(widget: QObject, apply_fn: Callable[[], None]) -> Callable[[], None]:
    """Connect themeChanged and disconnect on widget destroyed (no live self in lambda)."""
    slot = safe_theme_slot(apply_fn)
    bind_theme_changed(slot)

    def _on_destroyed(*_args: object) -> None:
        _disconnect_slot(slot)

    widget.destroyed.connect(_on_destroyed)
    return slot
