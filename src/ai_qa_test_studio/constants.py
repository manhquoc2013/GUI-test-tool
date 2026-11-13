"""Shared non-magic configuration values."""

from __future__ import annotations

DEFAULT_PROJECT_DIRNAME = "project"
DEFAULT_V4_PROJECT_FILENAME = "project.v4.json"
GROUP_JSON = "group.json"
SUITE_JSON = "suite.json"
SETTINGS_FILENAME = "settings.json"
CDP_HOST = "127.0.0.1"
CDP_PORT_EPHEMERAL = 0
TOKEN_MAX_ATOMIC_OUTPUT = 512
TOKEN_DOM_SOFT_CAP = 1200
SLIDING_HISTORY_SUMMARY_CHARS = 200
DEFAULT_LLM_MAX_TOKENS = 512
MICRO_TOOL_NAMES = (
    "navigate",
    "interact",
    "assert",
    "bind_step_element",
    "patch_element_locator",
    "load_skill",
    "finish_task",
)
ACTION_GOTO = "goto"
ACTION_MAP_JAVA_TO_V4 = {
    "NAVIGATE": "goto",
    "GOTO": "goto",
    "CLICK": "click",
    "FILL": "fill",
    "SELECT": "select",
    "HOVER": "hover",
    "PRESS_ENTER": "press_enter",
    "ASSERT_VISIBLE": "assert_visible",
    "ASSERT_TEXT": "assert_text",
    "ASSERT_URL": "assert_url",
    "VERIFY_DB": "verify_db",
    "DELAY": "hover",
    "RAW": "click",
    "NOTE": "assert_visible",
    "CUSTOM_JS": "click",
    "WAIT_FOR_ELEMENT": "assert_visible",
}
PRIORITY_MAP = {
    "P0": "P0 - Critical",
    "P1": "P1 - High",
    "P2": "P2 - Medium",
    "P3": "P3 - Low",
}
BINDING_UNBOUND = "UNBOUND"
BINDING_BOUND = "BOUND"
BINDING_HEALED = "HEALED"
VAR_PATTERN_LEGACY = r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}"
VAR_PATTERN_V4 = r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}"
