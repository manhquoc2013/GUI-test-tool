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
TOKEN_CONTEXT_BUDGET = 8000
SLIDING_HISTORY_SUMMARY_CHARS = 200
DEFAULT_LLM_MAX_TOKENS = 512

# UI workspace (Fluent QMainWindow shell)
UI_EXPLORER_MIN_WIDTH = 260
UI_TOP_BAR_HEIGHT = 52
UI_TABLE_ROW_HEIGHT = 38
UI_DRAWER_COLLAPSED_HEIGHT = 40
UI_DRAWER_EXPANDED_HEIGHT = 210
UI_VIEWPORT_WIDTH = 1280
UI_VIEWPORT_HEIGHT = 720
UI_VIEWPORT_MIN_HEIGHT = 240
UI_SPLIT_SIZES_H = (280, 680, 480)
UI_SPLIT_SIZES_V = (660, 200)
UI_WIZARD_MIN_WIDTH = 720
UI_WIZARD_MIN_HEIGHT = 560
UI_WIZARD_STEP_COUNT = 3
UI_PALETTE_MIN_WIDTH = 520
UI_PALETTE_MAX_HEIGHT = 420
UI_MARKDOWN_PREVIEW_CHARS = 4000
DEFAULT_TARGET_URL = "https://example.com"
FALLBACK_BLANK_URL = "about:blank"
DEFAULT_SKILLS_DIRNAME = "skills"
UI_INFOBAR_DURATION_MS = 6000
GOTO_TIMEOUT_MS = 30_000
STATUS_DOT = "●"
PARAM_CELL_SEP = " | "
THEME_ACCENT = "#6366F1"
THEME_BG_MAIN = "#18191C"
THEME_BG_PANEL = "#1A1B1E"
THEME_BG_TOPBAR = "#1E2025"
THEME_BG_STATUS = "#141518"
THEME_BORDER = "#2B2D32"
THEME_MUTED = "#9CA3AF"
THEME_RUN = "#10B981"
ICON_COLOR_DEFAULT = "#E2E8F0"
ICON_COLOR_PRIMARY = "#A5B4FC"
ICON_COLOR_SUCCESS = "#10B981"
ICON_COLOR_WARN = "#F59E0B"
ICON_COLOR_DANGER = "#F87171"
ICON_COLOR_HEALED = "#6366F1"
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
