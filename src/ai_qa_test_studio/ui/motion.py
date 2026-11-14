"""Fluent-style motion helpers — registry-backed animations with reduce_motion gate."""

from __future__ import annotations

from collections.abc import Callable, Iterator
from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import Any

from PySide6.QtCore import (
    QAbstractAnimation,
    QEasingCurve,
    QEvent,
    QObject,
    QPoint,
    QPropertyAnimation,
    QVariantAnimation,
)
from PySide6.QtWidgets import QGraphicsOpacityEffect, QSplitter, QWidget

from ai_qa_test_studio.constants import (
    UI_MOTION_CHAT_MS,
    UI_MOTION_FAST_MS,
    UI_MOTION_NORMAL_MS,
    UI_MOTION_SLIDE_PX,
    UI_WIDGET_SIZE_MAX,
)


def _widget_alive(widget: QObject) -> bool:
    try:
        from shiboken6 import isValid

        return bool(isValid(widget))
    except ImportError:  # pragma: no cover
        return True

Channel = str
RegistryKey = tuple[int, Channel]

CHANNEL_OPACITY = "opacity"
CHANNEL_WIDTH = "width"
CHANNEL_HEIGHT = "height"
CHANNEL_DIALOG_OPACITY = "dialog_opacity"
CHANNEL_SLIDE = "slide"

_EASING = QEasingCurve.Type.OutCubic


def _default_motion_enabled() -> bool:
    return True


def _noop() -> None:
    return None


_enabled_fn: Callable[[], bool] = _default_motion_enabled


@dataclass
class _RegistryEntry:
    animation: QAbstractAnimation
    widget_id: int
    channel: Channel
    cleanup: Callable[[], None] = field(default=_noop)
    destroyed_slot: Callable[..., None] | None = None
    finished_slot: Callable[[], None] | None = None
    value_slot: Callable[[Any], None] | None = None


_REGISTRY: dict[RegistryKey, _RegistryEntry] = {}


def bind_motion_enabled(fn: Callable[[], bool]) -> None:
    """Inject reduce_motion gate without importing MainWindow (avoids circular import)."""
    global _enabled_fn
    _enabled_fn = fn


def motion_enabled() -> bool:
    try:
        return bool(_enabled_fn())
    except Exception:  # noqa: BLE001 — never break UI if settings probe fails
        return True


def _key(widget: QObject, channel: Channel) -> RegistryKey:
    return (id(widget), channel)


def stop_channel(widget: QObject, channel: Channel) -> None:
    """Stop and unregister an in-flight animation on ``widget``/``channel``.

    Disconnects finished/value slots so a cancelled animation cannot run ``on_done``.
    """
    key = _key(widget, channel)
    entry = _REGISTRY.pop(key, None)
    if entry is None:
        return
    anim = entry.animation
    if entry.finished_slot is not None:
        try:
            anim.finished.disconnect(entry.finished_slot)
        except (TypeError, RuntimeError):
            pass
    if entry.value_slot is not None and isinstance(anim, QVariantAnimation):
        try:
            anim.valueChanged.disconnect(entry.value_slot)
        except (TypeError, RuntimeError):
            pass
    if anim.state() != QAbstractAnimation.State.Stopped:
        anim.stop()
    entry.cleanup()
    if entry.destroyed_slot is not None:
        try:
            widget.destroyed.disconnect(entry.destroyed_slot)
        except (TypeError, RuntimeError):
            pass


def _stop_channel(widget: QObject, channel: Channel) -> None:
    stop_channel(widget, channel)


def _register(
    widget: QObject,
    channel: Channel,
    animation: QAbstractAnimation,
    *,
    cleanup: Callable[[], None] | None = None,
    on_done: Callable[[], None] | None = None,
    value_slot: Callable[[Any], None] | None = None,
) -> None:
    _stop_channel(widget, channel)
    key = _key(widget, channel)
    cleanup_fn = cleanup or _noop

    def _destroyed(*_args: object) -> None:
        current = _REGISTRY.get(key)
        if current is None or current.animation is not animation:
            return
        _REGISTRY.pop(key, None)
        if current.finished_slot is not None:
            try:
                animation.finished.disconnect(current.finished_slot)
            except (TypeError, RuntimeError):
                pass
        if current.value_slot is not None and isinstance(animation, QVariantAnimation):
            try:
                animation.valueChanged.disconnect(current.value_slot)
            except (TypeError, RuntimeError):
                pass
        if animation.state() != QAbstractAnimation.State.Stopped:
            animation.stop()
        if _widget_alive(widget):
            cleanup_fn()

    def _finished() -> None:
        current = _REGISTRY.get(key)
        if current is None or current.animation is not animation:
            return
        _REGISTRY.pop(key, None)
        if entry.destroyed_slot is not None:
            try:
                widget.destroyed.disconnect(entry.destroyed_slot)
            except (TypeError, RuntimeError):
                pass
        if entry.value_slot is not None and isinstance(animation, QVariantAnimation):
            try:
                animation.valueChanged.disconnect(entry.value_slot)
            except (TypeError, RuntimeError):
                pass
        if _widget_alive(widget):
            cleanup_fn()
            if on_done is not None:
                on_done()
        elif on_done is not None:
            # Widget gone — skip UI callbacks that would touch deleted QObjects.
            pass

    entry = _RegistryEntry(
        animation=animation,
        widget_id=id(widget),
        channel=channel,
        cleanup=cleanup_fn,
        destroyed_slot=_destroyed,
        finished_slot=_finished,
        value_slot=value_slot,
    )
    _REGISTRY[key] = entry
    widget.destroyed.connect(_destroyed)
    animation.finished.connect(_finished)
    animation.start()


def _clear_opacity_effect(widget: QWidget) -> None:
    if not _widget_alive(widget):
        return
    try:
        widget.setGraphicsEffect(None)  # type: ignore[arg-type]
    except RuntimeError:
        return


def fade_in(
    widget: QWidget,
    *,
    duration_ms: int = UI_MOTION_CHAT_MS,
    on_done: Callable[[], None] | None = None,
) -> None:
    """Fade child widget 0→1 via temporary QGraphicsOpacityEffect; always remove effect."""
    if not _widget_alive(widget):
        return
    if not motion_enabled():
        _clear_opacity_effect(widget)
        if on_done is not None:
            on_done()
        return

    effect = QGraphicsOpacityEffect(widget)
    effect.setOpacity(0.0)
    widget.setGraphicsEffect(effect)
    anim = QPropertyAnimation(effect, b"opacity")
    anim.setDuration(duration_ms)
    anim.setStartValue(0.0)
    anim.setEndValue(1.0)
    anim.setEasingCurve(_EASING)
    _register(
        widget,
        CHANNEL_OPACITY,
        anim,
        cleanup=lambda: _clear_opacity_effect(widget),
        on_done=on_done,
    )


def fade_in_slide(
    widget: QWidget,
    *,
    duration_ms: int = UI_MOTION_CHAT_MS,
    slide_px: int = UI_MOTION_SLIDE_PX,
    on_done: Callable[[], None] | None = None,
) -> None:
    """Fade-in plus a short upward slide; clears QGraphicsOpacityEffect on finish."""
    if not _widget_alive(widget):
        return
    if not motion_enabled():
        _clear_opacity_effect(widget)
        if on_done is not None:
            on_done()
        return

    stop_channel(widget, CHANNEL_SLIDE)
    effect = QGraphicsOpacityEffect(widget)
    effect.setOpacity(0.0)
    widget.setGraphicsEffect(effect)
    start_pos = widget.pos()
    end_pos = start_pos
    offset = QPoint(start_pos.x(), start_pos.y() + max(0, slide_px))
    widget.move(offset)

    opacity = QPropertyAnimation(effect, b"opacity")
    opacity.setDuration(duration_ms)
    opacity.setStartValue(0.0)
    opacity.setEndValue(1.0)
    opacity.setEasingCurve(_EASING)

    pos_anim = QPropertyAnimation(widget, b"pos")
    pos_anim.setDuration(duration_ms)
    pos_anim.setStartValue(offset)
    pos_anim.setEndValue(end_pos)
    pos_anim.setEasingCurve(_EASING)

    # Drive both via opacity channel lifecycle; stop pos anim in cleanup.
    def _cleanup() -> None:
        if pos_anim.state() != QAbstractAnimation.State.Stopped:
            pos_anim.stop()
        if _widget_alive(widget):
            widget.move(end_pos)
            _clear_opacity_effect(widget)

    pos_anim.start()
    _register(
        widget,
        CHANNEL_OPACITY,
        opacity,
        cleanup=_cleanup,
        on_done=on_done,
    )


def fade_out_then(
    widget: QWidget,
    on_done: Callable[[], None],
    *,
    duration_ms: int = UI_MOTION_FAST_MS,
) -> None:
    if not motion_enabled():
        _clear_opacity_effect(widget)
        on_done()
        return

    effect = QGraphicsOpacityEffect(widget)
    effect.setOpacity(1.0)
    widget.setGraphicsEffect(effect)
    anim = QPropertyAnimation(effect, b"opacity")
    anim.setDuration(duration_ms)
    anim.setStartValue(1.0)
    anim.setEndValue(0.0)
    anim.setEasingCurve(_EASING)
    _register(
        widget,
        CHANNEL_OPACITY,
        anim,
        cleanup=lambda: _clear_opacity_effect(widget),
        on_done=on_done,
    )


def slide_height(
    widget: QWidget,
    start: int,
    end: int,
    *,
    duration_ms: int = UI_MOTION_NORMAL_MS,
    on_done: Callable[[], None] | None = None,
) -> None:
    """Animate height with min==max each tick; caller unlocks constraints in on_done."""
    start_i = int(start)
    end_i = int(end)

    def _apply(value: int) -> None:
        if not _widget_alive(widget):
            return
        widget.setMinimumHeight(value)
        widget.setMaximumHeight(value)

    if not motion_enabled() or start_i == end_i:
        _apply(end_i)
        if on_done is not None:
            on_done()
        return

    anim = QVariantAnimation()
    anim.setDuration(duration_ms)
    anim.setStartValue(start_i)
    anim.setEndValue(end_i)
    anim.setEasingCurve(_EASING)

    def _on_value(value: Any) -> None:
        if not _widget_alive(widget):
            return
        _apply(int(value))

    anim.valueChanged.connect(_on_value)
    _apply(start_i)
    _register(widget, CHANNEL_HEIGHT, anim, on_done=on_done, value_slot=_on_value)


def animate_splitter_size(
    splitter: QSplitter,
    index: int,
    start: int,
    end: int,
    *,
    host: QWidget,
    duration_ms: int = UI_MOTION_NORMAL_MS,
    on_done: Callable[[], None] | None = None,
) -> None:
    """Interpolate one splitter pane via setSizes; register on ``host`` width channel.

    Non-target side panes keep their start width (clamped to widget min/max).
    The stretch pane (index 1 when animating dock/explorer) absorbs the delta so
    we never allocate past ``maximumWidth`` and leave a dead gap in the splitter.
    """
    start_i = max(0, int(start))
    end_i = max(0, int(end))
    initial = list(splitter.sizes())
    pane_count = len(initial)
    if index >= pane_count:
        if on_done is not None:
            on_done()
        return

    stretch_i = (
        1
        if index != 1 and pane_count > 1
        else (0 if index != 0 else min(2, pane_count - 1))
    )
    fixed_sizes: dict[int, int] = {}
    for i, size in enumerate(initial):
        if i == index or i == stretch_i:
            continue
        widget = splitter.widget(i)
        clamped = max(0, size)
        if widget is not None:
            max_w = widget.maximumWidth()
            min_w = widget.minimumWidth()
            if 0 <= max_w < UI_WIDGET_SIZE_MAX:
                clamped = min(clamped, max_w)
            clamped = max(clamped, min_w) if widget.isVisible() else 0
        fixed_sizes[i] = clamped

    def _apply(dock_w: int) -> None:
        if not _widget_alive(host) or not _widget_alive(splitter):
            return
        locked = max(0, int(dock_w))
        # Lock min==max so Qt sizeHint cannot fight setSizes mid-animation
        # (otherwise the pane jumps to hint/max and the slide is invisible).
        host.setMinimumWidth(locked)
        host.setMaximumWidth(locked)
        total = max(sum(splitter.sizes()), splitter.width(), 1)
        new_sizes = [0] * pane_count
        new_sizes[index] = locked
        fixed_sum = 0
        for i, width in fixed_sizes.items():
            new_sizes[i] = width
            fixed_sum += width
        new_sizes[stretch_i] = max(0, total - new_sizes[index] - fixed_sum)
        splitter.setSizes(new_sizes)

    if not motion_enabled() or start_i == end_i:
        _apply(end_i)
        if on_done is not None:
            on_done()
        return

    anim = QVariantAnimation()
    anim.setDuration(duration_ms)
    anim.setStartValue(start_i)
    anim.setEndValue(end_i)
    anim.setEasingCurve(_EASING)

    def _on_value(value: Any) -> None:
        if not _widget_alive(host):
            return
        _apply(int(value))

    anim.valueChanged.connect(_on_value)
    _apply(start_i)
    _register(host, CHANNEL_WIDTH, anim, on_done=on_done, value_slot=_on_value)


@contextmanager
def suspend_updates(widget: QWidget) -> Iterator[None]:
    """Temporarily disable painting for batched layout/size churn."""
    if not _widget_alive(widget):
        yield
        return
    widget.setUpdatesEnabled(False)
    try:
        yield
    finally:
        if _widget_alive(widget):
            widget.setUpdatesEnabled(True)


class _DialogShowFilter(QObject):
    """One-shot Show filter that starts windowOpacity fade."""

    def __init__(self, dialog: QWidget, duration_ms: int) -> None:
        super().__init__(dialog)
        self._dialog = dialog
        self._duration_ms = duration_ms
        self._armed = True

    def eventFilter(self, watched: QObject, event: QEvent) -> bool:  # type: ignore[override]
        if not self._armed or watched is not self._dialog:
            return False
        if event.type() != QEvent.Type.Show:
            return False
        self._armed = False
        self._dialog.removeEventFilter(self)
        if not motion_enabled():
            self._dialog.setWindowOpacity(1.0)
            return False
        anim = QPropertyAnimation(self._dialog, b"windowOpacity")
        anim.setDuration(self._duration_ms)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setEasingCurve(_EASING)
        _register(self._dialog, CHANNEL_DIALOG_OPACITY, anim)
        return False


def animate_dialog_show(
    dialog: QWidget,
    *,
    duration_ms: int = UI_MOTION_FAST_MS,
) -> None:
    """Prime opacity 0 and fade in on first Show (works with show/open/exec)."""
    if not motion_enabled():
        dialog.setWindowOpacity(1.0)
        return
    dialog.setWindowOpacity(0.0)
    # Replacing prior filter/anim if dialog reused.
    _stop_channel(dialog, CHANNEL_DIALOG_OPACITY)
    filt = _DialogShowFilter(dialog, duration_ms)
    dialog.installEventFilter(filt)
    # Keep filter alive for Show (parented to dialog).
    dialog.setProperty("_motion_show_filter", filt)


def unlock_height(widget: QWidget, *, collapsed: bool = False, collapsed_height: int = 0) -> None:
    """Post-slide constraint restore."""
    if collapsed:
        widget.setMinimumHeight(collapsed_height)
        widget.setMaximumHeight(collapsed_height)
    else:
        widget.setMinimumHeight(0)
        widget.setMaximumHeight(UI_WIDGET_SIZE_MAX)


def registry_size() -> int:
    """Test helper — active animation entries."""
    return len(_REGISTRY)


def clear_registry_for_tests() -> None:
    """Test helper — stop all channels."""
    for key in list(_REGISTRY.keys()):
        entry = _REGISTRY.pop(key, None)
        if entry is None:
            continue
        anim = entry.animation
        if entry.finished_slot is not None:
            try:
                anim.finished.disconnect(entry.finished_slot)
            except (TypeError, RuntimeError):
                pass
        if entry.value_slot is not None and isinstance(anim, QVariantAnimation):
            try:
                anim.valueChanged.disconnect(entry.value_slot)
            except (TypeError, RuntimeError):
                pass
        if anim.state() != QAbstractAnimation.State.Stopped:
            anim.stop()
        entry.cleanup()


__all__ = [
    "CHANNEL_DIALOG_OPACITY",
    "CHANNEL_HEIGHT",
    "CHANNEL_OPACITY",
    "CHANNEL_SLIDE",
    "CHANNEL_WIDTH",
    "animate_dialog_show",
    "animate_splitter_size",
    "bind_motion_enabled",
    "clear_registry_for_tests",
    "fade_in",
    "fade_in_slide",
    "fade_out_then",
    "motion_enabled",
    "registry_size",
    "slide_height",
    "stop_channel",
    "suspend_updates",
    "unlock_height",
]
