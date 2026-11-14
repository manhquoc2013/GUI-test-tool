"""Shared non-magic configuration values."""

from __future__ import annotations

from enum import StrEnum

DEFAULT_PROJECT_DIRNAME = "project"
DEFAULT_V4_PROJECT_FILENAME = "project.v4.json"
GROUP_JSON = "group.json"
SUITE_JSON = "suite.json"
SETTINGS_FILENAME = "settings.json"
STUDIO_DIRNAME = ".studio"
STUDIO_RUNTIME_DB = "runtime.db"
STUDIO_ARTIFACTS_DIRNAME = "artifacts"
APP_STUDIO_DB = "studio.db"
PROJECT_YAML_FILENAME = "project.yaml"
PROJECT_SUITES_DIRNAME = "suites"
PROJECT_ELEMENTS_DIRNAME = "elements"
PROJECT_DATA_DIRNAME = "data"
THEME_PREF_AUTO = "auto"
THEME_PREF_DARK = "dark"
THEME_PREF_LIGHT = "light"
APP_SETTING_THEME = "theme"
SQLITE_CACHE_SIZE_KIB = -64000
SQLITE_MMAP_SIZE_BYTES = 268_435_456
KEYRING_SERVICE = "ai-qa-test-studio"
KEYRING_REF_PREFIX = "keyring:"
KEYRING_SLOT_LEGACY_API = "legacy-api"
CDP_HOST = "127.0.0.1"
CDP_PORT_EPHEMERAL = 0
TOKEN_MAX_ATOMIC_OUTPUT = 512
TOKEN_DOM_SOFT_CAP = 1200
TOKEN_CONTEXT_BUDGET = 8000
SLIDING_HISTORY_SUMMARY_CHARS = 200
DEFAULT_LLM_MAX_TOKENS = 512
COPILOT_DIRNAME = "copilot"
COPILOT_SESSIONS_FILENAME = "sessions.json"
COPILOT_SLIDING_TURNS = 8
COPILOT_CHAT_MAX_TOKENS = 2048
COPILOT_INPUT_HEIGHT = 64
COPILOT_BTN_SEND_SIZE = 36
COPILOT_CHIP_HEIGHT = 26
COPILOT_MODEL_COMBO_HEIGHT = 28
COPILOT_MODEL_COMBO_MIN_WIDTH = 88
COPILOT_MODEL_COMBO_MAX_WIDTH = 148
COPILOT_CONTEXT_FLOW_SPACING = 6
COPILOT_COMPOSER_GAP = 8
COPILOT_COMPOSER_PADDING = 8
COPILOT_STATUS_MIN_HEIGHT = 28
COPILOT_ATTACH_MAX_FILES = 5
COPILOT_ATTACH_PER_FILE_CHARS = 4000
COPILOT_ATTACH_TOTAL_CHARS = 8000
COPILOT_ATTACH_PREVIEW_CHARS = 800
COPILOT_ATTACH_BTN_LABEL = "File"
COPILOT_ATTACH_CHIP_HEIGHT = 28
COPILOT_ATTACH_NAME_MAX_CHARS = 42
DOC_CORPUS_DB_NAME = "doc_corpus.db"
DOC_CORPUS_CACHE_DIRNAME = "ai_qa_test_studio"
DOC_CORPUS_TMP_DB_NAME = "tmp_corpus.db"
DOC_CORPUS_SQLITE_TIMEOUT = 5.0
DOC_CORPUS_TMP_TTL_DAYS = 7
DOC_CHUNK_SIZE_CHARS = 1000
DOC_CHUNK_OVERLAP_CHARS = 150
DOC_EXCEL_ROWS_PER_CHUNK = 25
DOC_RETRIEVE_MAX_CHARS = 7000
DOC_RETRIEVE_TOP_K = 8
DOC_OVERVIEW_CHUNK_COUNT = 3
DOC_COVERAGE_MIN_CHUNKS = 4
DOC_COVERAGE_MAX_SECTIONS = 24
DOC_COVERAGE_CHUNKS_PER_SECTION = 3
DOC_COVERAGE_SECTION_MAX_CHARS = 6000
WIZARD_IMPORT_BATCH_SIZE = 150
CONTEXT_KEY_DOC_IDS = "doc_ids"
CONTEXT_KEY_RETRIEVE_QUERY = "retrieve_query"
COPILOT_STATUS_CONNECTING = "Đang gọi LLM… API lỗi sẽ tự thử lại (có thể ~60s)."
COPILOT_STATUS_PREPARING = "Đang chuẩn bị model / router…"
COPILOT_STATUS_CLARIFY = "Đang chờ bạn làm rõ…"
COPILOT_STATUS_WORKING = "{role} đang làm việc…"
COPILOT_BUSY_PLACEHOLDER = "Đang xử lý — bấm Stop để dừng…"
COPILOT_IDLE_PLACEHOLDER = "Nhập yêu cầu hoặc đính kèm file…"
COPILOT_MODEL_LABEL_AUTO = "Auto"
COPILOT_MODEL_HINT_AUTO = "Auto (theo tác vụ)"
COPILOT_CHIP_CASE = "@Case"
COPILOT_CHIP_DOM = "@DOM"
COPILOT_CHIP_ERROR = "@Error"
CONTEXT_KEY_ATTACHMENTS = "attachments"
ROLE_COORDINATOR = "coordinator"
ROLE_PLANNER = "planner"
ROLE_COMPOSER = "composer"
ROLE_CRITIC = "critic"
ROLE_ANALYZER = "analyzer"
ROLE_OPTIMIZER = "optimizer"
ROLE_CREW = "crew"
ROLE_SYSTEM = "system"
COORD_SPECIALISTS: tuple[str, ...] = (
    ROLE_PLANNER,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_ANALYZER,
    ROLE_OPTIMIZER,
)
COORD_ACTION_DISPATCH = "dispatch"
COORD_ACTION_COMPLETE = "complete"
COORD_ACTION_CLARIFY = "clarify"
COPILOT_COORDINATOR_MAX_ROUNDS = 8
CONTEXT_KEY_ANALYZER_ENABLED = "analyzer_enabled"
CONTEXT_KEY_OPTIMIZER_ENABLED = "optimizer_enabled"
LLM_TASK_ANALYZE = "analyze"
LLM_TASK_OPTIMIZE = "optimize"
LLM_PROVIDER_OFFLINE = "offline"
LLM_PROVIDER_OPENAI_COMPAT = "openai_compatible"
LLM_PROVIDER_GEMINI = "gemini"
LLM_MODEL_LIST_TIMEOUT_S = 20.0
COPILOT_MODE_COMPOSE = "compose"
COPILOT_MODE_CASE = "case_chat"
APPLY_KIND_COMPOSE = "compose_scenarios"
APPLY_KIND_TEST_DATA = "test_data_rows"
APPLY_KIND_STEPS = "steps_patch"
# Intent → compose artifact (Apply / Tải Excel). Typo-tolerant stems included.
COPILOT_COMPOSE_KEYWORDS: tuple[str, ...] = (
    "tạo kịch bản",
    "sinh kịch bản",
    "kịch bản",
    "srs",
    "scenario",
    "scenar",  # typo: screnaro / scenaro
    "draft",
    "test case",
    "test scen",
    "test scren",
    "phân tích",
    "tạo các test",
    "sinh test",
    "assertion",
    "excel",
    "trích xuất",
    "generate",
    "analyze",
)
COPILOT_COMPOSE_DOC_HINTS: tuple[str, ...] = (
    "tạo",
    "sinh",
    "phân tích",
    "analyze",
    "generate",
    "test",
    "draft",
    "excel",
    "kiểm thử",
    "kiem thu",
    "bộ kiểm",
    "bo kiem",
    "làm giúp",
    "lam giup",
    "giúp tôi",
    "giup toi",
    "file này",
    "file nay",
    "từ file",
    "tu file",
    "đính kèm",
    "dinh kem",
)
COPILOT_ARTIFACT_CTA_COMPOSE = (
    "→ {count} scenario draft sẵn sàng — bấm Tải Excel hoặc Apply vào Editor."
)
COPILOT_ARTIFACT_CTA_TEST_DATA = (
    "→ {count} bộ Test Data sẵn sàng — bấm Tải Excel hoặc Apply."
)
COPILOT_ARTIFACT_READY_NOTE = "Artifact sẵn sàng ({count}). Bấm Tải Excel hoặc Apply bên dưới."
# VN QA test-doc Excel (ISTQB / IEEE 829 style — aligned with legacy Test Studio)
VN_TC_SHEET_OVERVIEW = "Tổng quan"
VN_TC_SHEET_SCENARIOS = "Kịch bản"
VN_TC_SHEET_CASES = "Danh sách Test Cases"
VN_TC_SHEET_STEPS = "Chi tiết các bước"
VN_TC_SHEET_TEST_DATA = "Dữ liệu kiểm thử"
VN_TC_SHEET_IMPORT = "Test Cases (Import)"
VN_TC_OVERVIEW_TITLE = "TÀI LIỆU KỊCH BẢN KIỂM THỬ (TEST CASE SPECIFICATION)"
VN_TC_OVERVIEW_STANDARD = "ISTQB & IEEE 829 Test Case Specification Standard"
VN_TC_OVERVIEW_TOOL = "AI QA Test Studio — Copilot"
VN_TC_STATUS_DRAFT = "Bản nháp (Draft)"
VN_TC_COL_NUM = "STT"
VN_TC_COL_CASE_ID = "Mã Test Case"
VN_TC_COL_CASE_NAME = "Tên Test Case"
VN_TC_COL_GROUP = "Nhóm / Module"
VN_TC_COL_SUITE = "Bộ Test (Scenario)"
VN_TC_COL_PRIORITY = "Độ ưu tiên"
VN_TC_COL_STATUS = "Trạng thái"
VN_TC_COL_TYPE = "Loại kiểm thử"
VN_TC_COL_PRECONDITIONS = "Điều kiện tiên quyết"
VN_TC_COL_STEPS_SUMMARY = "Các bước thực hiện"
VN_TC_COL_TEST_DATA = "Dữ liệu kiểm thử"
VN_TC_COL_EXPECTED = "Kết quả mong đợi"
VN_TC_COL_REQUIREMENT = "Yêu cầu (Requirement)"
VN_TC_COL_NOTES = "Mã kịch bản / Ghi chú"
VN_TC_COL_SCENARIO_ID = "Mã kịch bản"
VN_TC_COL_SCENARIO_TITLE = "Tên kịch bản"
VN_TC_COL_DESCRIPTION = "Mô tả"
VN_TC_COL_STEP_NO = "Bước #"
VN_TC_COL_ACTION = "Hành động (Action)"
VN_TC_COL_TARGET = "Đối tượng (Target / Element)"
VN_TC_COL_STEP_DESC = "Mô tả bước"
VN_TC_DEFAULT_GROUP = "Copilot"
VN_TC_DEFAULT_PRECONDITION = "Môi trường kiểm thử sẵn sàng, trình duyệt Chromium"
VN_TC_EXCEL_MAX_CELL_CHARS = 32700
VN_TC_HEADER_FILL = "1E3A8A"
VN_TC_HEADER_FONT_COLOR = "FFFFFF"
VN_TC_ZEBRA_FILL = "F8FAFC"
VN_TC_FILENAME_PREFIX = "Kich_ban_kiem_thu_"
VN_TC_FILENAME_DEFAULT = "Kich_ban_kiem_thu_Copilot.xlsx"
VN_TC_FILE_FILTER = "Excel chuẩn QA VN (*.xlsx)"
COPILOT_BTN_DOWNLOAD_EXCEL = "Tải Excel"
COPILOT_BTN_APPLY = "Apply vào Editor / Test Data"
COPILOT_DOWNLOAD_TOOLTIP = "Tải đầy đủ artifact ra Excel chuẩn tài liệu test VN (không cần Apply)"
COPILOT_APPLY_TOOLTIP = "Áp dụng artifact Copilot vừa tạo"
COPILOT_DOWNLOAD_DIALOG_TITLE = "Tải kịch bản kiểm thử ra Excel"
COPILOT_DOWNLOAD_DONE_TITLE = "Đã tải Excel"
COPILOT_DOWNLOAD_EMPTY = "Chưa có artifact để tải. Chờ Copilot sinh kịch bản / test data."
COPILOT_DOWNLOAD_FAIL_TITLE = "Không tải được Excel"
MSG_STATUS_STOPPED = "stopped"
MSG_STATUS_COMPLETE = "complete"
CASE_STATUS_CANCELLED = "CANCELLED"
CASE_STATUS_PASS = "PASS"
CASE_STATUS_FAIL = "FAIL"
RUN_CANCEL_WAIT_TIMEOUT_S = 30.0
COPILOT_SUMMARY_MAX_CHARS = 160
COPILOT_CHAT_REPLY_MAX_CHARS = 220
COPILOT_ROLE_REPLY_STYLE = (
    "Trả lời tiếng Việt, ngắn gọn: tối đa 4 gạch đầu dòng hoặc ~80 từ. "
    "Không viết đoạn dài; chi tiết nằm trong artifact/Apply."
)
# NEXUS/Ruflo-inspired handoff + deliverable contracts (Studio domain — not vendored).
COPILOT_HANDOFF_HEADER = "[HANDOFF]"
COPILOT_ROLE_BRIEFS: dict[str, str] = {
    ROLE_COORDINATOR: (
        "Bạn là Điều phối viên (hierarchical lead). Nắm vững ngữ cảnh board, "
        "giao việc / thu hồi kết quả từ chuyên gia, quyết định bước tiếp. "
        "Không soạn artifact thay chuyên gia. Output JSON điều phối thôi."
    ),
    ROLE_PLANNER: (
        "Bạn là Chuyên gia hoạch định. Chỉ lập kế hoạch giao việc — không soạn artifact. "
        "Output bắt buộc: (1) Mục tiêu 1 câu; (2) 3–5 bước giao việc "
        "ghi rõ agent nhận (kiểm thử/phản biện/phân tích/tối ưu); "
        "(3) Tiêu chí xong (acceptance). Không bịa dữ liệu thiếu."
    ),
    ROLE_COMPOSER: (
        "Bạn là Chuyên gia kiểm thử. Thực thi đúng handoff từ hoạch định. "
        "Output: tóm tắt bubble tiếng Việt + artifact JSON khi intent=compose|test_data. "
        "Không yêu cầu đúng keyword; hiểu diễn đạt tự nhiên / lỗi chính tả."
    ),
    ROLE_CRITIC: (
        "Bạn là Chuyên gia phản biện (quality gate). Chỉ APPROVE hoặc REVISE. "
        "REVISE phải liệt kê lỗ hổng cụ thể (thiếu expected, BVA, mâu thuẫn SRS). "
        "Không APPROVE khi thiếu bằng chứng — tránh fantasy approval."
    ),
    ROLE_ANALYZER: (
        "Bạn là Chuyên gia phân tích rủi ro/lỗi. Output: nguyên nhân khả dĩ, "
        "tín hiệu quan sát, gợi ý heal — có căn cứ từ Composer/Critic."
    ),
    ROLE_OPTIMIZER: (
        "Bạn là Chuyên gia tối ưu suite. Output: P0 cần giữ, case gộp/bỏ, "
        "lỗ hổng coverage — ưu tiên actionable."
    ),
}
COPILOT_AGENT_DEBATE_MAX_ROUNDS = 3
COPILOT_AGENT_LOOP_REPEAT_LIMIT = 2
COPILOT_CLARIFY_TIMEOUT_MS = 60_000
COPILOT_LEAN_TAIL_MAX_TOKENS = 2400
COPILOT_MODEL_AUTO = "auto"
COPILOT_STARTER_PROMPTS: tuple[tuple[str, str], ...] = (
    ("Sinh Test Data biên", "Sinh thêm 5 bộ Test Data biên (BVA)"),
    ("Giải thích lỗi bước", "Giải thích lỗi bước vừa chạy"),
    ("Chuyển YAML", "Chuyển kịch bản sang YAML"),
    ("Assertions", "Tạo kịch bản / Assertions từ SRS"),
)
AGENT_FEED_MAX_LINES = 5
LLM_TASK_COORDINATE = "coordinate"
LLM_TASK_PLAN = "plan"
LLM_TASK_COMPOSE = "compose"
LLM_TASK_REVISE = "revise"
LLM_TASK_CRITIQUE = "critique"
LLM_TASK_DEBATE = "debate"
LLM_TASK_SUMMARIZE = "summarize"
LLM_TASK_CLARIFY_DRAFT = "clarify_draft"
LLM_TASK_INTENT = "intent"
INTENT_COMPOSE = "compose"
INTENT_TEST_DATA = "test_data"
INTENT_EXPLAIN = "explain"
INTENT_VALUES: tuple[str, ...] = (INTENT_COMPOSE, INTENT_TEST_DATA, INTENT_EXPLAIN)
LLM_TIER_FAST = "fast"
LLM_TIER_MID = "mid"
LLM_TIER_STRONG = "strong"
COMPLEXITY_SIMPLE = "simple"
COMPLEXITY_NORMAL = "normal"
COMPLEXITY_COMPLEX = "complex"
COMPLEXITY_SIMPLE_MAX_CHARS = 120
COMPLEXITY_NORMAL_MAX_CHARS = 800
COMPLEXITY_LARGE_DOC_CHARS = 2400
COMPLEXITY_LABELS_VN: dict[str, str] = {
    COMPLEXITY_SIMPLE: "dễ",
    COMPLEXITY_NORMAL: "trung bình",
    COMPLEXITY_COMPLEX: "khó",
}
# Ruflo QE / ECC model-route inspired signals — QA Studio domain.
COMPLEXITY_EXPLAIN_HINTS: tuple[str, ...] = (
    "giải thích",
    "explain",
    "vì sao",
    "why",
    "lỗi bước",
    "debug",
)
COMPLEXITY_NORMAL_HINTS: tuple[str, ...] = (
    "bva",
    "test data",
    "biên",
    "bộ dữ liệu",
    "assertion",
    "một scenario",
    "1 scenario",
    "smoke",
    "happy path",
)
COMPLEXITY_HARD_HINTS: tuple[str, ...] = (
    "toàn bộ",
    "nhiều scenario",
    "multi",
    "e2e",
    "end-to-end",
    "security",
    "bảo mật",
    "chaos",
    "full suite",
    "toàn bộ srs",
    "phân tích sâu",
    "architecture",
    "kiến trúc",
)
MCP_QA_COMPRESS = "qa-compress"
MCP_QA_CREW_MEMORY = "qa-crew-memory"
MCP_QA_PROJECT_CONTEXT = "qa-project-context"
MCP_QA_MIDSCENE = "qa-midscene"
MCP_QA_PLAYWRIGHT = "qa-playwright"
MCP_QA_INGEST = "qa-ingest"
MCP_QA_PERSISTENT_MEMORY = "qa-persistent-memory"
MCP_TOOL_LOCATE = "locate"
MCP_TOOL_DOCUMENT_TO_MARKDOWN = "document_to_markdown"
MCP_TOOL_MEMORY_PUT = "put_note"
MCP_TOOL_MEMORY_GET = "get_notes"
MCP_TOOL_MEMORY_SEARCH = "search_notes"
DEFAULT_MCP_SERVERS_FILENAME = "mcp_servers.json"
EVENT_STATUS_SUMMARY = "status_summary"
EVENT_AGENT_MESSAGE = "agent_message"
EVENT_CLARIFY_REQUEST = "clarify_request"
EVENT_CLARIFY_RESOLVED = "clarify_resolved"
EVENT_LOOP_HALTED = "loop_halted"
EVENT_TOOL_CALL = "tool_call"
EVENT_CHUNK = "chunk"
CLARIFY_SOURCE_USER = "user"
CLARIFY_SOURCE_DEFAULT_TIMEOUT = "default_timeout"
CLARIFY_SOURCE_DEFAULT_SKIP = "default_skip"
CLARIFY_SOURCE_STOPPED = "stopped"
CLARIFY_DIALOG_TITLE = "Làm rõ yêu cầu"
CLARIFY_DIALOG_HEADING = "{role} cần làm rõ"
CLARIFY_DIALOG_CUSTOM_PLACEHOLDER = "Hoặc tự nhập câu trả lời…"
CLARIFY_DIALOG_OK = "OK"
CLARIFY_DIALOG_USE_DEFAULT = "Dùng gợi ý đã chọn"
CLARIFY_GENERIC_QUESTION = (
    "Cần chọn hướng xử lý trước khi sinh artifact — bạn muốn gì?"
)
CLARIFY_OPT_COMPOSE_HAPPY = "Sinh kịch bản happy path (UNBOUND) rồi cho Tải Excel / Apply"
CLARIFY_OPT_COMPOSE_FULL = "Sinh kèm negative + BVA (nhiều case hơn)"
CLARIFY_OPT_EXPLAIN_ONLY = "Chỉ giải thích / lập kế hoạch — chưa sinh kịch bản"
CLARIFY_OPT_BVA_EDITOR = "Sinh Test Data biên cho case đang mở trên Editor"
CLARIFY_OPT_BVA_LOGIN = "Sinh Test Data biên mẫu cho form login (user/password)"
CLARIFY_OPT_ATTACH_SRS = "Dùng tài liệu / SRS đã đính kèm làm nguồn"
CLARIFY_OPT_WAIT_DETAIL = "Dừng lại — tôi sẽ nhập thêm chi tiết ngay"
CLARIFY_FALLBACK_DEFAULT = CLARIFY_OPT_COMPOSE_HAPPY
CLARIFY_AMBIGUITY_MARKERS = (
    "chưa rõ",
    "unclear",
    "ambiguous",
    "cần làm rõ",
    "???",
    "không biết",
)
ROUTING_MODE_AUTO = "auto"
ROUTING_MODE_MANUAL = "manual"
UI_SETTINGS_MIN_WIDTH = 820
UI_SETTINGS_MIN_HEIGHT = 580
UI_SETTINGS_CONTENT_HEIGHT = 400
UI_SETTINGS_LIST_HEIGHT = 150
UI_SETTINGS_BTN_MIN_WIDTH = 112
UI_SETTINGS_BTN_MAX_WIDTH = 140
UI_SETTINGS_FIELD_SPACING = 12
UI_SETTINGS_SECTION_GAP = 14
UI_SETTINGS_MODEL_COMBO_PLACEHOLDER = "Gõ để tìm model từ API…"
UI_SETTINGS_MODEL_COMBO_TOOLTIP = (
    "Danh sách chỉ gồm model tải từ API nhà cung cấp. Gõ để lọc/tìm tên model."
)
UI_SETTINGS_MODEL_UNSET_LABEL = "(chưa chọn model)"
UI_SETTINGS_MODEL_COMPLETER_MAX_VISIBLE = 12
DEFAULT_COPILOT_MODELS = (
    COPILOT_MODEL_AUTO,
)

# App identity / branding
APP_DISPLAY_NAME = "AI QA Test Studio"
APP_WINDOW_TITLE = "AI QA Test Studio v4.2"
APP_ICON_DIRNAME = "icons"
APP_ICON_MASTER_FILENAME = "app_icon.png"
APP_ICON_FILENAME_TEMPLATE = "app_icon_{size}.png"
APP_ICON_SIZES: tuple[int, ...] = (16, 32, 48, 64, 128, 256, 512)

# UI workspace (Fluent QMainWindow shell) — v4.2 three-column
UI_WINDOW_MIN_WIDTH = 980
UI_WINDOW_MIN_HEIGHT = 640
UI_WINDOW_DEFAULT_WIDTH = 1440
UI_WINDOW_DEFAULT_HEIGHT = 880
UI_BREAKPOINT_WIDE = 1440
UI_BREAKPOINT_MEDIUM = 1150
UI_THREE_COL_SAFE = 1240
UI_SIDEBAR_WIDTH = 260
UI_SIDEBAR_WIDTH_COMPACT = 230
UI_SIDEBAR_WIDTH_MAX = 320
UI_SIDEBAR_MAX_RATIO = 0.42
UI_DRAWER_WIDTH = 380
UI_DRAWER_WIDTH_COMPACT = 360
UI_DRAWER_WIDTH_NARROW = 340
UI_DOCK_MIN_WIDTH = 340
UI_DOCK_MAX_WIDTH = 450
UI_WIDGET_SIZE_MAX = 16777215
UI_BTN_COPILOT_MIN_WIDTH = 105
UI_EXPLORER_MIN_WIDTH = 230
UI_EXPLORER_DENSITY_WIDE = 320
UI_EXPLORER_CONTENT_H_MARGIN = 16
UI_EDITOR_MIN_WIDTH = 520
UI_STEPS_COL_INDEX_WIDTH = 44
UI_STEPS_COL_ACTION_WIDTH = 88
UI_STEPS_COL_BINDING_WIDTH = 118
UI_STEPS_HEADER_MIN_SECTION = 40
UI_UNBOUND_BANNER_TITLE = "Có bước chưa gắn selector (UNBOUND)"
UI_UNBOUND_BANNER_BODY = (
    "Nhập Target URL trên thanh công cụ rồi nhấn Sync để Agent tự động ánh xạ selector từ DOM."
)
UI_DATA_COL_SCENARIO_WIDTH = 210
UI_DATA_COL_EXPECTED_WIDTH = 105
UI_DATA_HEADER_MIN_SECTION = 72
UI_DATA_TABLE_ROW_HEIGHT = 36
UI_TREE_INDENT = 14
UI_BROWSER_PANE_MIN_WIDTH = 320
UI_TOP_BAR_HEIGHT = 48
UI_STATUS_BAR_HEIGHT = 28
UI_TABLE_ROW_HEIGHT = 42
UI_TREE_ROW_HEIGHT = 34
UI_MSG_ACTION_BTN_SIZE = 28
CHAT_BUBBLE_MAX_WIDTH = 520
CHAT_BUBBLE_RADIUS = 12
CHAT_BUBBLE_PADDING_H = 12
CHAT_BUBBLE_PADDING_V = 10
CHAT_BUBBLE_GAP = 8
CHAT_BUBBLE_ROW_MARGIN = 6
CHAT_ROLE_BADGE_PAD_H = 8
CHAT_ROLE_BADGE_PAD_V = 2
CHAT_ROLE_BADGE_RADIUS = 8
CHAT_ROLE_BADGE_FONT_PX = 11
CHAT_BODY_FONT_PX = 13
CHAT_TRANSCRIPT_SPACING = 10
CHAT_ROLE_LABELS: dict[str, str] = {
    "user": "Bạn",
    ROLE_COORDINATOR: "Điều phối viên",
    ROLE_PLANNER: "Chuyên gia hoạch định",
    ROLE_COMPOSER: "Chuyên gia kiểm thử",
    ROLE_CRITIC: "Chuyên gia phản biện",
    ROLE_ANALYZER: "Chuyên gia phân tích",
    ROLE_OPTIMIZER: "Chuyên gia tối ưu",
    ROLE_CREW: "Đội Copilot",
    ROLE_SYSTEM: "Hệ thống",
    "assistant": "Trợ lý AI",
}
UI_DRAWER_COLLAPSED_HEIGHT = 42
UI_DRAWER_EXPANDED_HEIGHT = 210
UI_DRAWER_EXPAND_MIN_HEIGHT = 150
UI_VIEWPORT_WIDTH = 1280
UI_VIEWPORT_HEIGHT = 720
UI_VIEWPORT_MIN_HEIGHT = 160
UI_SPLIT_SIZES_H = (260, 800, 380)
UI_SPLIT_SIZES_V = (540, 190)
UI_CANVAS_MARGIN_H = 20
UI_CANVAS_MARGIN_V = 14
UI_CANVAS_MARGIN_H_COMPACT = 12
UI_CANVAS_MARGIN_V_COMPACT = 10
UI_CARD_GAP = 12
UI_SETTINGS_NAV_WIDTH = 175
UI_SETTINGS_SPIN_MIN_WIDTH = 200
UI_SETTINGS_SPIN_HEIGHT = 33
UI_BTN_WIZARD_MIN_WIDTH = 115
UI_BTN_SYNC_MIN_WIDTH = 104
UI_BTN_RUN_MIN_WIDTH = 96
UI_BTN_STOP_SIZE = 34
UI_BTN_EXPORT_MIN_WIDTH = 98
UI_BTN_INSPECT_MIN_WIDTH = 96
UI_BTN_LAUNCH_MIN_WIDTH = 100
UI_URL_INPUT_MIN_WIDTH = 220
UI_URL_INPUT_MIN_WIDTH_COMPACT = 160
UI_URL_INPUT_MIN_WIDTH_NARROW = 120
UI_LABEL_WIZARD = "Tạo bằng AI"
UI_LABEL_WIZARD_SHORT = "Tạo AI"
UI_ACTION_BADGE_WIDTH = 64
UI_ACTION_BADGE_HEIGHT = 24
UI_TITLE_MIN_WIDTH = 120
UI_TITLE_FONT_SIZE_PX = 18
UI_LABEL_COPILOT = "Copilot"
UI_LABEL_RUN = "Chạy"
UI_LABEL_STOP = "Dừng"
UI_FLUENT_ICON_LEFT_PAD = 12
UI_LABEL_INSPECT = "Inspect"
UI_LABEL_INSPECT_ON = "Inspect ON"
UI_LABEL_BROWSER = "Browser"
class ExpectedOutcome(StrEnum):
    SUCCESS = "SUCCESS"
    VALIDATION_ERROR = "VALIDATION_ERROR"
    ERROR = "ERROR"
    EXCEPTION = "EXCEPTION"
    REDIRECT = "REDIRECT"


DEFAULT_EXPECTED_OUTCOME = ExpectedOutcome.SUCCESS
EXPECTED_OUTCOME_VALUES: tuple[str, ...] = tuple(member.value for member in ExpectedOutcome)
BATCH_CANCELLED_MESSAGE = "Cancelled by user"
TEST_DATA_NEW_ROW_LABEL = "Bộ dữ liệu mới"
UI_TEST_DATA_DIALOG_MIN_WIDTH = 480
UI_TEST_DATA_PARAM_ROW_SPACING = 6
SCREENCAST_STATUS_WAITING = "Đã kết nối CDP — Đang chờ tải trang..."
SCREENCAST_STATUS_NAV_FAILED = (
    "Không tải được trang — đã mở trang trống. Nhập URL thật rồi Launch lại."
)
THEME_CARD_RADIUS = 8
UI_WIZARD_MIN_WIDTH = 680
UI_WIZARD_MIN_HEIGHT = 520
UI_WIZARD_STEP_COUNT = 3
UI_PALETTE_MIN_WIDTH = 520
UI_PALETTE_MAX_HEIGHT = 420
UI_MARKDOWN_PREVIEW_CHARS = 4000
UI_WIZARD_DOC_LIST_MIN_HEIGHT = 96
WIZARD_DOC_KIND_SRS = "srs"
WIZARD_DOC_KIND_TEST_SCENARIO = "test_scenario"
WIZARD_DOC_KIND_TEST_CASE = "test_case"
WIZARD_DOC_KIND_LABELS = {
    WIZARD_DOC_KIND_SRS: "SRS",
    WIZARD_DOC_KIND_TEST_SCENARIO: "Test Scenario",
    WIZARD_DOC_KIND_TEST_CASE: "Test Case",
}
WIZARD_DOC_FILE_FILTER = (
    "Documents (*.md *.txt *.csv *.tsv *.xlsx *.xlsm *.docx *.pdf *.html *.htm)"
)
WIZARD_DOC_MARKDOWN_SUFFIXES = (".md", ".markdown", ".txt")
WIZARD_DOC_CSV_SUFFIXES = (".csv", ".tsv")
WIZARD_DOC_XLSX_SUFFIXES = (".xlsx", ".xlsm")
WIZARD_DOC_CLASSIFY_SRS_TOKENS = (
    "srs",
    "spec",
    "requirement",
    "prd",
    "dac-ta",
    "dacta",
    "dac ta",
)
WIZARD_DOC_CLASSIFY_SCENARIO_TOKENS = (
    "scenario",
    "test-scenario",
    "test_scenario",
    "kich-ban",
    "kichban",
)
WIZARD_DOC_CLASSIFY_CASE_TOKENS = (
    "testcase",
    "test-case",
    "test_case",
    "test case",
    "truong-hop",
    "truonghop",
)
WIZARD_DOC_HEADER_CASE_ID = "test case id"
WIZARD_DOC_HEADER_SCENARIO_ID = "scenario id"
WIZARD_DOC_HEADER_STEP_NO = "step no"
WIZARD_DOC_HEADER_ACTION = "action"
WIZARD_DOC_HEADER_TITLE = "title"
WIZARD_DOC_HEADER_TEST_DATA = "test data"
WIZARD_DOC_HEADER_EXPECTED = "expected result"
WIZARD_DOC_HEADER_PRECONDITIONS = "pre-conditions"
WIZARD_DOC_HEADER_REQUIREMENT = "requirement"
WIZARD_DOC_HEADER_DESCRIPTION = "description"
WIZARD_DOC_SHEET_SCENARIOS = "test scenarios"
WIZARD_DOC_SHEET_CASES = "test cases"
WIZARD_DOC_CONTEXT_CAP_CHARS = 6000
WIZARD_DOC_MAX_FILE_BYTES = 50_000_000
WIZARD_DOC_MAX_TABLE_ROWS = 500
WIZARD_DOC_PREVIEW_TABLE_ROWS = 40
WIZARD_DOC_MAX_SHEETS = 20
WIZARD_DOC_SAMPLE_MARKDOWN = "Sample SRS: login and checkout happy path"
WIZARD_DOC_HEADER_COMPACT_ALIASES = {
    "testcaseid": WIZARD_DOC_HEADER_CASE_ID,
    "testid": WIZARD_DOC_HEADER_CASE_ID,
    "tcid": WIZARD_DOC_HEADER_CASE_ID,
    "tc": WIZARD_DOC_HEADER_CASE_ID,
    "scenarioid": WIZARD_DOC_HEADER_SCENARIO_ID,
    "scenariocode": WIZARD_DOC_HEADER_SCENARIO_ID,
    "tsid": WIZARD_DOC_HEADER_SCENARIO_ID,
    "stepno": WIZARD_DOC_HEADER_STEP_NO,
    "stepnumber": WIZARD_DOC_HEADER_STEP_NO,
    "step": WIZARD_DOC_HEADER_STEP_NO,
    "action": WIZARD_DOC_HEADER_ACTION,
    "title": WIZARD_DOC_HEADER_TITLE,
    "name": WIZARD_DOC_HEADER_TITLE,
    "testdata": WIZARD_DOC_HEADER_TEST_DATA,
    "inputdata": WIZARD_DOC_HEADER_TEST_DATA,
    "data": WIZARD_DOC_HEADER_TEST_DATA,
    "expectedresult": WIZARD_DOC_HEADER_EXPECTED,
    "expected": WIZARD_DOC_HEADER_EXPECTED,
    "preconditions": WIZARD_DOC_HEADER_PRECONDITIONS,
    "precondition": WIZARD_DOC_HEADER_PRECONDITIONS,
    "requirement": WIZARD_DOC_HEADER_REQUIREMENT,
    "requirementid": WIZARD_DOC_HEADER_REQUIREMENT,
    "reqid": WIZARD_DOC_HEADER_REQUIREMENT,
    "description": WIZARD_DOC_HEADER_DESCRIPTION,
    "desc": WIZARD_DOC_HEADER_DESCRIPTION,
    "intent": "intent",
}
DEFAULT_TARGET_URL = "https://example.com"
FALLBACK_BLANK_URL = "about:blank"
DEFAULT_SKILLS_DIRNAME = "skills"
UI_INFOBAR_DURATION_MS = 6000
GOTO_TIMEOUT_MS = 30_000
STATUS_DOT = "●"
PARAM_CELL_SEP = " | "
# Design tokens (§3) — solid fallbacks + semantic action colors
THEME_ACCENT = "#6366F1"
THEME_BG_CANVAS = "#141518"
THEME_BG_SURFACE = "#191B20"
THEME_BG_ELEVATED = "#20232A"
THEME_BORDER_SUBTLE = "#24262D"
THEME_TEXT_PRIMARY = "#F3F4F6"
THEME_TEXT_ON_LIGHT = "#111827"
THEME_TEXT_SECONDARY = "#9CA3AF"
THEME_TEXT_MUTED = "#71717A"
THEME_STATUS_BOUND = "#34D399"
THEME_STATUS_UNBOUND = "#FBBF24"
THEME_STATUS_HEALED = "#818CF8"
THEME_STATUS_ERROR = "#EF4444"
THEME_ACTION_CLICK_FG = "#38BDF8"
THEME_ACTION_FILL_FG = "#34D399"
THEME_ACTION_SELECT_FG = "#A78BFA"
THEME_ACTION_ASSERT_FG = "#FBBF24"
THEME_ACTION_FALLBACK_FG = "#94A3B8"
THEME_SURFACE_PANEL_ALPHA = 0.02
THEME_SURFACE_CARD_ALPHA = 0.025
THEME_SURFACE_BORDER_ALPHA = 0.08
THEME_UNBOUND_BG_ALPHA = 0.10
THEME_UNBOUND_BORDER_ALPHA = 0.30
THEME_ACTION_BADGE_BG_ALPHA = 0.15
# Legacy aliases (keep imports working)
THEME_BG_MAIN = THEME_BG_CANVAS
THEME_BG_PANEL = THEME_BG_SURFACE
THEME_BG_TOPBAR = THEME_BG_CANVAS
THEME_BG_CARD = THEME_BG_SURFACE
THEME_BG_STATUS = THEME_BG_CANVAS
THEME_BG_SCREENCAST = "#0D0E10"
THEME_BORDER = THEME_BORDER_SUBTLE
THEME_BORDER_CARD = THEME_BORDER_SUBTLE
THEME_MUTED = THEME_TEXT_SECONDARY
THEME_RUN = THEME_STATUS_BOUND
ICON_COLOR_DEFAULT = THEME_TEXT_PRIMARY
ICON_COLOR_PRIMARY = "#A5B4FC"
ICON_COLOR_SUCCESS = THEME_STATUS_BOUND
ICON_COLOR_WARN = THEME_STATUS_UNBOUND
ICON_COLOR_DANGER = THEME_STATUS_ERROR
ICON_COLOR_HEALED = THEME_STATUS_HEALED
MICRO_TOOL_LOAD_SKILL = "load_skill"
MICRO_TOOL_NAMES = (
    "navigate",
    "interact",
    "assert",
    "bind_step_element",
    "patch_element_locator",
    MICRO_TOOL_LOAD_SKILL,
    "finish_task",
)
SKILL_CONTEXT_MAX_CHARS = 4000
CONTEXT_KEY_SKILL_CATALOG = "skill_catalog"
CONTEXT_KEY_SKILL_BODY = "skill_body"
CONTEXT_KEY_SKILL_NAME = "skill_name"
CONTEXT_KEY_PROJECT_ROOT = "project_root"
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
