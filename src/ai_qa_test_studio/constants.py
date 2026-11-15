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
STUDIO_RUNS_DIRNAME = "runs"
STUDIO_AUTH_DIRNAME = "auth"
DEFAULT_AUTH_PROFILE = "default"
AUTH_GITIGNORE_ENTRY = ".studio/auth/"
LOGIN_PATH_MARKERS = ("/login", "/signin", "/sso", "/auth/login", "/oauth")
EVIDENCE_MANIFEST_FILENAME = "manifest.json"
EVIDENCE_AUDIT_FILENAME = "healing_audit.json"
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
MODEL_TOKEN_PROFILE_API_TIMEOUT_S = 3.0
MODEL_TOKEN_DEFAULT_CONTEXT = 65_536
MODEL_TOKEN_DEFAULT_MAX_OUTPUT = 8_192
MODEL_TOKEN_SOURCE_API = "api"
MODEL_TOKEN_SOURCE_CATALOG = "catalog"
MODEL_TOKEN_SOURCE_CACHE = "cache"
MODEL_TOKEN_SOURCE_DEFAULT = "default"
MODEL_TOKEN_LABEL_API = "Tự động (API)"
MODEL_TOKEN_LABEL_CATALOG = "Tự động (Catalog)"
MODEL_TOKEN_LABEL_CACHE = "Tự động (Cache)"
MODEL_TOKEN_LABEL_DEFAULT = "Mặc định an toàn (8,192)"
SLIDING_HISTORY_SUMMARY_CHARS = 200
DEFAULT_LLM_MAX_TOKENS = 512
COPILOT_DIRNAME = "copilot"
COPILOT_SESSIONS_FILENAME = "sessions.json"
COPILOT_HISTORY_POPOVER_WIDTH = 288
COPILOT_HISTORY_POPOVER_MAX_HEIGHT = 280
COPILOT_HISTORY_POPOVER_MIN_HEIGHT = 96
COPILOT_HISTORY_POPOVER_MARGIN = 8
COPILOT_HISTORY_POPOVER_GAP = 6
COPILOT_HISTORY_HEADER_HEIGHT = 36
COPILOT_HISTORY_ROW_HEIGHT = 34
COPILOT_HISTORY_ROW_GAP = 2
COPILOT_HISTORY_VISIBLE_ROWS = 7
COPILOT_HISTORY_EMPTY_HEIGHT = 48
COPILOT_HISTORY_TITLE_MAX_CHARS = 42
COPILOT_HISTORY_SHADOW_BLUR = 28
COPILOT_HISTORY_SHADOW_OFFSET_Y = 8
COPILOT_HISTORY_SHADOW_ALPHA = 70
# QPA platforms where QGraphicsDropShadowEffect / widget.grab() often SIGABRT.
QT_QPA_HEADLESS_PLATFORMS = frozenset({"offscreen", "minimal", "null"})
COPILOT_HISTORY_TITLE = "Lịch sử"
COPILOT_HISTORY_TOGGLE_TIP = "Hiện/ẩn lịch sử chat"
COPILOT_HISTORY_EMPTY = "Chưa có cuộc trò chuyện"
COPILOT_HISTORY_BADGE_RUNNING = "⏳"
COPILOT_HISTORY_BADGE_UNREAD = "●"
COPILOT_HISTORY_DELETE_TIP = "Xóa phiên này khỏi lịch sử"
COPILOT_HISTORY_ROW_DELETE_SIZE = 26
COPILOT_SLIDING_TURNS = 8
COPILOT_CHAT_MAX_TOKENS = 2048
COPILOT_GEN_MAX_TOKENS = 8192
COPILOT_GEN_MAX_TOKENS_CAP = 16384
COPILOT_INPUT_HEIGHT = 64
COPILOT_BTN_SEND_SIZE = 36
COPILOT_STOP_MARKER = "— Đã dừng —"
COPILOT_INCOMPLETE_MARKER = "— Chưa hoàn tất —"
COPILOT_STOP_SUFFIX = f"\n\n{COPILOT_STOP_MARKER}"
COPILOT_STREAM_LENGTH_NOTICE = (
    "\n\n⚠️ Phản hồi tạm dừng do đạt giới hạn độ dài. "
    "Nhập 'tiếp tục' để sinh nốt phần còn lại."
)
# Auto-continue when finish_reason=length before surfacing the notice above.
LLM_STREAM_MAX_CONTINUATIONS = 3
LLM_STREAM_CONTINUE_PRIOR_CHARS = 6000
LLM_STREAM_PROGRESS_GUARD_RATIO = 0.80
LLM_STREAM_STALL_NOTICE = (
    "\n\n⚠️ Dừng nối tiếp tự động: đoạn mới rỗng hoặc trùng lặp >80% "
    "với phần cuối lượt trước. Nhập 'tiếp tục' nếu vẫn cần sinh thêm."
)
LLM_STREAM_CONTINUE_PROMPT = (
    "Phản hồi trước bị cắt giữa chừng vì giới hạn độ dài. "
    "Hãy viết tiếp CHÍNH XÁC từ điểm ngắt — không lặp lại tiêu đề, "
    "phần mở đầu, hay các dòng đã có. "
    "Nếu đang viết JSON, chỉ nối tiếp đúng cú pháp còn thiếu.\n\n"
    "Nội dung đã sinh (neo ngữ cảnh):\n{prior}"
)
COPILOT_STREAM_ERROR_PREFIX = "⚠️ Lỗi khi đọc phản hồi model:\n"
# Map-Reduce batch compose for large suites (300–500+ cases).
STUDIO_DRAFTS_DIRNAME = "drafts"
COPILOT_BATCH_SCENARIO_SIZE = 5
COPILOT_BATCH_SEMAPHORE = 2
COPILOT_BATCH_SCENARIO_THRESHOLD = 5
COPILOT_BATCH_CASE_THRESHOLD = 25
COPILOT_LARGE_SUITE_UI_CASE_THRESHOLD = 25
COPILOT_BATCH_PROGRESS = (
    "Đang sinh bộ Test Case: {done}/{total} cases (Lô {batch}/{batches})…"
)
COPILOT_BATCH_RESUME_HINT = (
    "\n\n⏸ Đã lưu checkpoint các lô hoàn tất. Nhập 'tiếp tục' để chạy nốt các lô còn lại."
)
COPILOT_LARGE_SUITE_SAMPLES = 5
APPLY_PAYLOAD_DRAFT_SESSION_KEY = "draft_session_id"
COPILOT_URL_BROWSER_OFF_NOTE = (
    "ℹ️ Browser/MCP chưa bật — scenario dựa trên prompt (chưa đọc cấu trúc trang)."
)
COPILOT_STOP_ICON_INSET_RATIO = 0.28
COPILOT_STOP_ICON_RADIUS_RATIO = 0.14
COPILOT_STOP_TOOLTIP = "Dừng phản hồi Copilot"
COPILOT_CHIP_HEIGHT = 26
COPILOT_CHIP_MIN_WIDTH = 64
COPILOT_CHIP_ERROR_MIN_WIDTH = 72
COPILOT_MODEL_COMBO_HEIGHT = 28
COPILOT_MODEL_COMBO_MIN_WIDTH = 88
COPILOT_MODEL_COMBO_MAX_WIDTH = 148
COPILOT_CONTEXT_FLOW_SPACING = 6
COPILOT_CONTEXT_ROW_MIN_HEIGHT = 36
COPILOT_COMPOSER_GAP = 8
COPILOT_COMPOSER_PADDING = 8
COPILOT_STATUS_MIN_HEIGHT = 28
LLM_FINISH_REASON_LENGTH = "length"
LLM_FINISH_REASON_STOP = "stop"
URL_HTTP_RE = r"https?://[^\s<>\"'`\])\},]+"
URL_CONTEXT_MAX_CHARS = 6000
URL_FETCH_TIMEOUT_S = 12.0
CHAT_MD_TABLE_CSS = (
    "table{width:100%;table-layout:fixed;border-collapse:collapse;margin:6px 0;}"
    "th,td{border:1px solid #94A3B8;padding:4px 8px;text-align:left;"
    "vertical-align:top;word-wrap:break-word;overflow-wrap:anywhere;}"
    "th{font-weight:600;}"
)
COPILOT_ATTACH_MAX_FILES = 5
COPILOT_ATTACH_PER_FILE_CHARS = 4000
COPILOT_ATTACH_TOTAL_CHARS = 8000
COPILOT_ATTACH_PREVIEW_CHARS = 800
COPILOT_ATTACH_BTN_LABEL = "File"
COPILOT_ATTACH_BTN_MIN_WIDTH = 36
COPILOT_ATTACH_CHIP_HEIGHT = 28
COPILOT_ATTACH_NAME_MAX_CHARS = 42
COPILOT_STRUCTURED_DRAFT_BANNER = (
    '[Bản nháp — Bấm "Áp dụng vào dự án" để lưu]'
)
COPILOT_JSON_STREAM_PROGRESS = (
    "Đang cấu trúc bộ kịch bản kiểm thử (đã tạo {count} nhóm)…"
)
COPILOT_TEST_CASE_GEN_MAX_CASES = 8
COPILOT_CRITIC_COMPOSER_DIGEST_CHARS = 1200
COPILOT_COMPOSER_INPUT_MIN_WIDTH = 80
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
CTX_MARKER_SECTION = "SECTION:"
CTX_MARKER_RETRIEVED = "RETRIEVED:"
CTX_MARKER_SRS = "SRS:"
CTX_MARKER_ATTACHMENTS = "ATTACHMENTS:"
CTX_MARKER_SKILLS = "SKILLS:"
CTX_MARKER_ACTIVE_SKILL = "ACTIVE_SKILL"
CTX_MARKER_DOM = "DOM:"
CTX_MARKER_CASE = "CASE:"
CTX_MARKER_LAST_ERROR = "LAST_ERROR:"
COPILOT_RESULT_LINE_MAX = 160
COPILOT_FINAL_REPORT_CLOSING_MAX = 120
COPILOT_FINAL_REPORT_NOTE_MAX = 100
SPEC_OUTLINE_COVER_RULE = (
    "Cover main flows, alternate/exception flows, validations, and notifications "
    "found in the SRS — emit as many scenarios as the source warrants "
    "(not only Happy / Negative / Edge)."
)
COPILOT_STATUS_CONNECTING = "Đang gọi LLM… API lỗi sẽ tự thử lại (có thể ~60s)."
COPILOT_STATUS_PREPARING = "Đang chuẩn bị model / router…"
COPILOT_STATUS_CLARIFY = "Đang chờ bạn làm rõ…"
COPILOT_STATUS_WORKING = "{role} đang làm việc…"
COPILOT_STATUS_WAITING_LLM = "Đang chờ phản hồi model…"
COPILOT_STATUS_STREAMING = "Đang nhận phản hồi từ model…"
COPILOT_STATUS_CLASSIFYING = "Đang phân loại độ khó / chọn model…"
COPILOT_STATUS_INFERRING_INTENT = "Đang nhận diện intent…"
COPILOT_STATUS_COORD_DECIDE = "Điều phối viên đang chọn bước tiếp…"
COPILOT_STATUS_INDEXING = "Đang index tài liệu đính kèm…"
COPILOT_STATUS_ROLE_HANDOFF = "{role} đang chờ · đã giao → {next_role}"
COPILOT_STATUS_ROLE_ROUTED = "✓ Đã định tuyến → {next_role}"
COPILOT_STATUS_WAITING_FRAGMENT = "đang chờ"
COPILOT_COORD_COMPLETE_FALLBACK = "Hoàn tất điều phối"
COPILOT_COORD_JSON_HIDDEN = "Đã điều phối xong"
COPILOT_COORD_EXPLAIN_GOAL = "Trả lời trực tiếp câu hỏi người dùng"
COPILOT_COORD_EXPLAIN_ACCEPTANCE = (
    "Câu trả lời rõ ràng; không bắt chọn sản phẩm QA hay mở hộp thoại làm rõ"
)
CLARIFY_SUGGESTION_MIN_CHARS = 2
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
LLM_JSON_LOG_PREVIEW_CHARS = 180
LLM_OUTLINE_MAX_TOKENS = 4096
LLM_JSON_ONLY_RULE = (
    "Reply with JSON only — no markdown fences, no commentary before or after. "
    "Keep each object compact (short description); never put raw line breaks inside strings; "
    "finish every bracket so the JSON is complete."
)
COPILOT_MODE_COMPOSE = "compose"
COPILOT_MODE_CASE = "case_chat"
APPLY_KIND_COMPOSE = "compose_scenarios"
APPLY_KIND_TEST_DATA = "test_data_rows"
APPLY_KIND_STEPS = "steps_patch"
APPLY_KIND_SWARM_AUDIT = "swarm_audit"
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
# Full readable chat/explain replies (paragraphs + blank-line separators).
COPILOT_CHAT_PROSE_MAX_CHARS = 8_000
COPILOT_ROLE_REPLY_STYLE = (
    "Trả lời tiếng Việt, ngắn gọn: tối đa 4 gạch đầu dòng hoặc ~80 từ. "
    "Không viết đoạn dài; chi tiết nằm trong artifact/Apply."
)
COPILOT_CHAT_PROSE_STYLE = (
    "Trả lời tiếng Việt, đầy đủ và dễ đọc: tách ý bằng xuống dòng, "
    "chừa một dòng trống giữa các đoạn, dùng gạch đầu dòng khi liệt kê. "
    "Không gom toàn bộ thành một dòng liền. "
    "Trả lời thẳng câu hỏi người dùng — không tự xưng Chuyên gia kiểm thử/composer, "
    "không viết mục Báo cáo cho Điều phối viên, không liệt kê Intent/Skill/RETRIEVED."
)
COPILOT_EXPLAIN_META_MARKERS = (
    "báo cáo cho điều phối",
    "báo cáo cho điều phối viên",
    "intent nhận được",
    "skill đang gắn",
    "ngữ cảnh retrieved",
    "không phát sinh artifact",
)
# NEXUS/Ruflo-inspired handoff + deliverable contracts (Studio domain — not vendored).
COPILOT_HANDOFF_HEADER = "[HANDOFF]"
COPILOT_ROLE_BRIEFS: dict[str, str] = {
    ROLE_COORDINATOR: (
        "Bạn là Điều phối viên (hierarchical lead). Tự phân tích Intent + board "
        "bằng lý luận (không dựa keyword cứng). Giao việc / thu hồi kết quả từ "
        "chuyên gia, quyết định bước tiếp. Không soạn artifact thay chuyên gia. "
        "Không complete khi chưa có câu trả lời/artifact. Output JSON điều phối "
        "thôi (JSON không hiện cho user)."
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
        "Tôn trọng stage isolation: test_scenario = scenario cấp cao (không lồng cases/steps); "
        "test_case_gen = cases/steps cho scenario chỉ định (tối đa 8 case/lượt). "
        "Không yêu cầu đúng keyword; hiểu diễn đạt tự nhiên / lỗi chính tả."
    ),
    ROLE_CRITIC: (
        "Bạn là Chuyên gia phản biện (quality gate). Chỉ APPROVE hoặc REVISE. "
        "REVISE: bullet ngắn theo mã (code), dạng diff — không lặp lại JSON Composer. "
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
EVENT_FINAL_REPORT = "final_report"
EVENT_CLARIFY_REQUEST = "clarify_request"
EVENT_CLARIFY_RESOLVED = "clarify_resolved"
EVENT_LOOP_HALTED = "loop_halted"
EVENT_TOOL_CALL = "tool_call"
EVENT_CHUNK = "chunk"
EVENT_ERROR = "error"
EVENT_TURN_END = "turn_end"
COPILOT_FINAL_REPORT_MAX_CHARS = 420
COPILOT_FINAL_REPORT_EMPTY = "Hoàn tất — chưa có artifact/kết quả để áp dụng."
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
UI_SETTINGS_MIN_HEIGHT = 640
UI_SETTINGS_CONTENT_HEIGHT = 420
UI_SETTINGS_LIST_HEIGHT = 150
UI_SETTINGS_BTN_MIN_WIDTH = 112
UI_SETTINGS_BTN_MAX_WIDTH = 140
UI_SETTINGS_FIELD_SPACING = 12
UI_SETTINGS_SECTION_GAP = 14
UI_SETTINGS_HEADER_MARGIN_H = 28
UI_SETTINGS_HEADER_MARGIN_TOP = 24
UI_SETTINGS_HEADER_MARGIN_BOTTOM = 12
UI_SETTINGS_FOOTER_MIN_HEIGHT = 56
UI_SETTINGS_MODEL_COMBO_PLACEHOLDER = "Gõ để tìm model từ API…"
UI_SETTINGS_MODEL_COMBO_TOOLTIP = (
    "Danh sách chỉ gồm model tải từ API nhà cung cấp. Gõ để lọc/tìm tên model."
)
UI_SETTINGS_MODEL_UNSET_LABEL = "(chưa chọn model)"
UI_SETTINGS_MODEL_COMPLETER_MAX_VISIBLE = 12
UI_SETTINGS_MODEL_TOKEN_SPIN_MIN = 256
UI_SETTINGS_MODEL_TOKEN_SPIN_MAX = 1_048_576
UI_SETTINGS_MODEL_TOKEN_FIELD_LABEL = "Max output tokens"
DEFAULT_COPILOT_MODELS = (
    COPILOT_MODEL_AUTO,
)

# App identity / branding
APP_DISPLAY_NAME = "AI QA Test Studio"
APP_EXE_NAME = "AIQATestStudio"
APP_WINDOW_TITLE = "AI QA Test Studio v4.1.2"
APP_PUBLISHER = "Mạnh Quốc"
APP_PUBLISHER_EMAIL = "manhquoc11@gmail.com"
APP_PUBLISHER_URL = "https://github.com/manhquoc11"
APP_IDENTIFIER = "com.manhquoc.aiqateststudio"
APP_COPYRIGHT = "Copyright © 2026 Mạnh Quốc"
APP_SUPPORT_DIRNAME = "AIQATestStudio"
APP_LINUX_OPT_DIR = "/opt/ai-qa-test-studio"
APP_DATA_DIRNAME = "data"
APP_BROWSERS_DIRNAME = "browsers"
PLAYWRIGHT_BROWSERS_PATH_ENV = "PLAYWRIGHT_BROWSERS_PATH"
CHROMIUM_INSTALL_TIMEOUT_SEC = 1800
CHROMIUM_PROGRESS_PERCENT_RE = r"(\d+)\s*%"
CHROMIUM_BINARY_GLOBS: tuple[str, ...] = (
    "chromium-*/**/chrome",
    "chromium-*/**/chrome.exe",
    "chromium_headless_shell-*/**/chrome-headless-shell",
    "chromium_headless_shell-*/**/chrome-headless-shell.exe",
    "chromium-*/**/Chromium",
    "chromium-*/**/Chromium.app/Contents/MacOS/Chromium",
)
UI_CHROMIUM_DOWNLOAD_TITLE = "Đang tải Chromium"
UI_CHROMIUM_DOWNLOAD_BODY = "Cần Chromium để Launch / Sync / Run. Tiến độ tải xuống:"
UI_CHROMIUM_CANCELLED = "Đã hủy tải Chromium"
UI_CHROMIUM_FAILED = "Không tải được Chromium"
UI_DATA_FALLBACK_NOTICE = "Thư mục data cạnh app không ghi được — dùng thư mục người dùng."
UI_ABOUT_TITLE = "Giới thiệu"
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
UI_STEPS_COL_EXPECTED_WIDTH = 160
UI_STEPS_COL_BINDING_WIDTH = 118
UI_STEPS_HEADER_MIN_SECTION = 40
UI_STEPS_COL_COUNT = 6
UI_UNBOUND_BANNER_TITLE = "Có bước chưa gắn selector (UNBOUND)"
UI_UNBOUND_BANNER_BODY = (
    "Nhập Target URL trên thanh công cụ rồi nhấn Sync để Agent tự động ánh xạ selector từ DOM."
)
UI_DATA_COL_SCENARIO_WIDTH = 210
UI_DATA_COL_EXPECTED_WIDTH = 168
UI_DATA_HEADER_MIN_SECTION = 96
UI_DATA_TABLE_ROW_HEIGHT = 36
UI_TREE_INDENT = 14
UI_BROWSER_PANE_MIN_WIDTH = 320
UI_TOP_BAR_HEIGHT = 48
UI_STATUS_BAR_HEIGHT = 28
UI_TABLE_ROW_HEIGHT = 42
UI_TREE_ROW_HEIGHT = 34
UI_MSG_ACTION_BTN_SIZE = 28
CHAT_BUBBLE_MIN_WIDTH = 180
CHAT_BUBBLE_PANEL_RATIO = 0.85
CHAT_BUBBLE_RADIUS = 12
CHAT_BUBBLE_PADDING_H = 12
CHAT_BUBBLE_PADDING_V = 10
CHAT_BUBBLE_GAP = 8
CHAT_BUBBLE_ROW_MARGIN = 6
CHAT_TRANSCRIPT_PAD = 8
CHAT_ROLE_BADGE_PAD_H = 8
CHAT_ROLE_BADGE_PAD_V = 2
CHAT_ROLE_BADGE_RADIUS = 8
CHAT_ROLE_BADGE_FONT_PX = 11
CHAT_BODY_FONT_PX = 13
CHAT_TRANSCRIPT_SPACING = 10
CHAT_TYPING_DOT_COUNT = 3
CHAT_TYPING_DOT_DIAMETER = 7
CHAT_TYPING_DOT_GAP = 6
CHAT_TYPING_DOT_BOUNCE_PX = 3
CHAT_TYPING_INTERVAL_MS = 160
CHAT_TYPING_PHASES = 8
CHAT_TYPING_LABEL = "Đang xử lý…"
CHAT_TYPING_EMPTY_MARKERS: frozenset[str] = frozenset({"", "…", "...", "⋯"})
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
UI_MOTION_FAST_MS = 150
UI_MOTION_NORMAL_MS = 220
UI_MOTION_CHAT_MS = 180
UI_MOTION_SLIDE_PX = 10
UI_RESIZE_DEBOUNCE_MS = 24
UI_SPLITTER_PAINT_PAUSE_MS = 16
UI_SCROLL_NEAR_BOTTOM_PX = 48
UI_JUMP_LATEST_LABEL = "Cuộn xuống mới nhất"
UI_JUMP_LATEST_FG_LIGHT = "#1a1a1a"
UI_JUMP_LATEST_FG_DARK = "#ffffff"
UI_JUMP_LATEST_PADDING_H = 14
UI_JUMP_LATEST_PADDING_V = 6
UI_JUMP_LATEST_RADIUS = 16
UI_JUMP_LATEST_BORDER_PX = 1
UI_THINKING_STATUS = "Đang phân tích / Đang suy luận…"
UI_MCP_WARMUP_LABEL = "MCP: đang warm-up…"
UI_EXPORT_PROGRESS_TITLE = "Đang xuất báo cáo"
UI_EXPORT_PROGRESS_BODY = "Ghi file trên luồng nền…"
UI_EVIDENCE_LOADING = "Đang tải ảnh evidence…"
UI_EVIDENCE_TAB_LABEL = "Kết quả"
UI_EVIDENCE_EMPTY = "Chưa có kết quả chạy — bấm Chạy để thu thập bằng chứng."
UI_EVIDENCE_NO_SHOT = "Bước này không có ảnh chụp màn hình."
UI_EVIDENCE_MISSING_SHOT = "Không tìm thấy file ảnh bằng chứng trên đĩa."
UI_EVIDENCE_FIT = "Vừa khung"
UI_EVIDENCE_FULL = "100%"
UI_EVIDENCE_ZOOM = "Phóng to"
UI_EVIDENCE_ACCEPT_HEAL = "Chấp nhận & Lưu selector này"
UI_EVIDENCE_HEAL_TEMP = "Heal tạm thời để chạy tiếp — Chưa ghi đè YAML"
UI_EVIDENCE_HEAL_PERSISTED = "Đã tự động cập nhật ElementFingerprint bền vững vào YAML"
UI_EVIDENCE_SELECTOR_PREV = "Previous Selector"
UI_EVIDENCE_SELECTOR_HEALED = "Healed Selector"
REPORT_EXPORT_TASK_KEY = "report:export"
UI_PROGRESS_RING_SIZE = 18
UI_STEP_SPINNER_SIZE = 16
COPILOT_STATUS_THINKING = "Đang phân tích / Đang suy luận…"
UI_CANVAS_MARGIN_H = 20
UI_CANVAS_MARGIN_V = 14
UI_CANVAS_MARGIN_H_COMPACT = 12
UI_CANVAS_MARGIN_V_COMPACT = 10
UI_CARD_GAP = 12
UI_SETTINGS_NAV_WIDTH = 175
UI_SETTINGS_SPIN_MIN_WIDTH = 200
UI_SETTINGS_SPIN_HEIGHT = 36
UI_SETTINGS_SPIN_LINE_MIN_WIDTH = 40
UI_SETTINGS_SPIN_LINE_LEFT = 10
UI_SETTINGS_SPIN_BUTTONS_RESERVE = 80
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
WIZARD_DOC_HEADER_TARGET = "target"
WIZARD_DOC_HEADER_INTENT = "intent"
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
    "datainput": WIZARD_DOC_HEADER_TEST_DATA,
    "data": WIZARD_DOC_HEADER_TEST_DATA,
    "expectedresult": WIZARD_DOC_HEADER_EXPECTED,
    "expected": WIZARD_DOC_HEADER_EXPECTED,
    "preconditions": WIZARD_DOC_HEADER_PRECONDITIONS,
    "precondition": WIZARD_DOC_HEADER_PRECONDITIONS,
    "requirement": WIZARD_DOC_HEADER_REQUIREMENT,
    "requirementid": WIZARD_DOC_HEADER_REQUIREMENT,
    "target": WIZARD_DOC_HEADER_TARGET,
    "element": WIZARD_DOC_HEADER_TARGET,
    "object": WIZARD_DOC_HEADER_TARGET,
    "intent": WIZARD_DOC_HEADER_INTENT,
    "intenthint": WIZARD_DOC_HEADER_INTENT,
    "reqid": WIZARD_DOC_HEADER_REQUIREMENT,
    "description": WIZARD_DOC_HEADER_DESCRIPTION,
    "desc": WIZARD_DOC_HEADER_DESCRIPTION,
}
DEFAULT_TARGET_URL = "https://example.com"
COPILOT_SUITE_TITLE_SUFFIX = " — Copilot Suite"
PLACEHOLDER_PROJECT_NAMES = frozenset(
    {
        "Imported Legacy Project",
        "New Project",
    }
)
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
STEP_WAIT_DEFAULT_MS = 500
STEP_SCROLL_DEFAULT_DELTA_Y = 600
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

# Self-heal dual thresholds (Sprint 2+)
HEAL_RUN_THRESHOLD = 0.65
HEAL_PERSIST_THRESHOLD = 0.85
HEAL_NAME_HEURISTIC_CONFIDENCE = 0.70
HEAL_VISION_DEFAULT_CONFIDENCE = 0.70

# SWARM_AUDIT finding / failure codes
AUDIT_CODE_MISSING_EVIDENCE = "MISSING_EVIDENCE"
AUDIT_CODE_FALSE_POSITIVE_PASS = "FALSE_POSITIVE_PASS"
AUDIT_CODE_HEAL_REVIEW = "HEAL_REVIEW"
AUDIT_FAIL_SELECTOR_DRIFT = "SELECTOR_DRIFT"
AUDIT_FAIL_ASSERTION_MISMATCH = "ASSERTION_MISMATCH"
AUDIT_FAIL_APP_RUNTIME_ERROR = "APP_RUNTIME_ERROR"
AUDIT_FAIL_ENVIRONMENT_TIMEOUT = "ENVIRONMENT_TIMEOUT"
ASSERT_STEP_ACTIONS = frozenset(
    {"assert", "assert_visible", "assert_text", "assert_url", "verify_db"}
)
AUDIT_HTTP_ERROR_RE = r"(?i)\b(?:HTTP[/\s-]?)?(?:4\d{2}|5\d{2})\b"
AUDIT_CONSOLE_ERROR_RE = r"(?i)\b(?:uncaught|console\s*error|TypeError|ReferenceError)\b"
AUDIT_SELECTOR_DRIFT_RE = r"(?i)\b(?:stale|selector|locator|not\s*found|no\s*node|element)\b"
AUDIT_ASSERT_MISMATCH_RE = r"(?i)\b(?:assert|expected|mismatch|not\s*equal|timeout\s*waiting\s*for\s*text)\b"
AUDIT_TIMEOUT_RE = r"(?i)\b(?:timeout|timed\s*out|environment|net::ERR|ECONNREFUSED)\b"
COPILOT_AUDIT_EXPORT_TIP = "Xuất báo cáo Audit (HTML / Excel)"

# Toolbar Run task keys / UX copy
RUN_TASK_KEY_PREFIX = "run:"
RUN_URL_EMPTY_TITLE = "Target URL"
RUN_URL_EMPTY_BODY = "Nhập Target URL hợp lệ (http/https) trước khi chạy."
RUN_URL_INVALID_TITLE = "Target URL không hợp lệ"
RUN_URL_INVALID_BODY = "URL phải có scheme http/https (hoặc file://) và host/path hợp lệ."
RUN_UNBOUND_TITLE = "Chưa Sync locator"
RUN_UNBOUND_BODY = (
    "Còn bước UNBOUND — hãy Sync URL trước, hoặc bật «Sync trước khi chạy» trong Settings."
)
RUN_PARTIAL_BIND_TITLE = "Một phần chưa BOUND"
RUN_PARTIAL_BIND_BODY = (
    "Ưu tiên chạy bước BOUND; các bước UNBOUND có thể thất bại hoặc cần Agent/Sync."
)
RUN_ALLOWED_URL_SCHEMES = frozenset({"http", "https", "file"})

# Fingerprint score weights (identity stack ≈ 1.0; frame/shadow are contextual bonuses)
FP_WEIGHT_TEST_ID = 0.45
FP_WEIGHT_ROLE_NAME = 0.25
FP_WEIGHT_CSS = 0.15
FP_WEIGHT_XPATH = 0.10
FP_WEIGHT_TEXT = 0.05
FP_WEIGHT_FRAME_PATH = 0.08
FP_WEIGHT_SHADOW_DOM = 0.04
FP_FRAME_MISMATCH_FACTOR = 0.55

# SnapshotDistiller
SNAPSHOT_MAX_NODES = 80
SNAPSHOT_INCLUDE_ARIA_DEFAULT = False
SNAPSHOT_MAX_FRAME_DEPTH = 5
SNAPSHOT_IFRAME_MIN_SIZE_PX = 2
ATTR_IN_SHADOW_DOM = "in_shadow_dom"
ATTR_SHADOW_HOST_CHAIN = "shadow_host_chain"
FRAME_PATH_NTH_SEP = ">> nth="
AUTH_CAPTURE_TASK_KEY = "auth:capture"
UI_LABEL_AUTH_SESSION = "Phiên đăng nhập"
UI_LABEL_AUTH_SAVE = "Lưu phiên"
UI_AUTH_EXPIRED_TITLE = "Phiên đăng nhập hết hạn"
UI_AUTH_EXPIRED_BODY = (
    "URL bị chuyển về trang đăng nhập — mở «Phiên đăng nhập» để đăng nhập lại và lưu storageState."
)

# DualLoop MCP-first (Sprint 4+); falls back to in-process page when executor unavailable
MCP_ACTIONS_ENABLED = True

# SkillAutoMatcher
SKILL_MATCH_WEIGHT_INTENT = 0.45
SKILL_MATCH_WEIGHT_KEYWORD = 0.30
SKILL_MATCH_WEIGHT_WORKSPACE = 0.25
SKILL_MATCH_MIN_SCORE = 0.45
SKILL_MATCH_MAX_PER_TURN = 2

# ThinkingPolicy token budgets
THINKING_BUDGET_OFF = 0
THINKING_BUDGET_LOW = 768
THINKING_BUDGET_MEDIUM = 3072
THINKING_BUDGET_HIGH = 8192

# ContextCompactor pin markers + snapshot/tool retention
COMPACTOR_PIN_PREFIX = "[[PIN:"
COMPACTOR_PIN_SUFFIX = "]]"
COMPACTOR_SNAPSHOT_MARKER = "browser_snapshot"
COMPACTOR_SNAPSHOT_STUB_FROM_AGE = 2  # keep t-0..t-1 full; stub browser_snapshot from t-2+
COMPACTOR_KEEP_LAST_TOOL_PAIRS = 2
COMPACTOR_SNAPSHOT_STUB_TEMPLATE = "[stub:{marker} age=t-{age} omitted]"
COMPACTOR_TOOL_ROLE_NAMES = frozenset({"tool", "function", "tool_call", "tool_result"})
COMPACTOR_TOOL_CONTENT_MARKERS = (
    "tool_call",
    "tool_result",
    "browser_click",
    "browser_fill",
    "browser_navigate",
    "browser_type",
)
COMPACTOR_SYSTEM_ROLES = frozenset({"system"})
COMPACTOR_TESTCASE_MARKERS = ("testcase", "test_case", "[[pin:testcase", "[[pin:case")
COMPACTOR_CREW_DRAFT_ROLES = frozenset(
    {ROLE_PLANNER, ROLE_COMPOSER, ROLE_CRITIC, ROLE_COORDINATOR}
)
COMPACTOR_CREW_FINAL_ROLES = frozenset({ROLE_COMPOSER, ROLE_COORDINATOR, "assistant"})

# MCP error taxonomy class names
MCP_ERR_TRANSIENT = "TRANSIENT"
MCP_ERR_STALE_REF = "STALE_REF"
MCP_ERR_BAD_ARGS = "BAD_ARGS"
MCP_ERR_DOM_CHANGED = "DOM_CHANGED"
MCP_ERR_FATAL = "FATAL"
MCP_EXECUTOR_MAX_RETRIES = 2

# Bindable step actions (case_binding_summary)
BINDABLE_STEP_ACTIONS = frozenset(
    {
        "click",
        "fill",
        "select",
        "hover",
        "press_enter",
        "assert",
        "assert_visible",
        "assert_text",
    }
)
NON_BINDABLE_STEP_ACTIONS = frozenset(
    {
        "goto",
        "navigate",
        "scroll",
        "wait",
        "assert_url",
        "verify_db",
        "api_request",
    }
)

# Slash → IntentType
INTENT_SLASH_MAP = {
    "/chat": "chat_fast",
    "/research": "research",
    "/scenario": "test_scenario",
    "/tc": "test_case_gen",
    "/script": "test_script_gen",
    "/run": "exec_and_bind",
    "/audit": "swarm_audit",
}
APPLY_PROMPT_RE = r"(?i)^\s*(?:apply|/apply)\b"

# Outbound SecretMasker
SECRET_MASK_PLACEHOLDER = "***MASKED***"
SECRET_MASK_KEY_HINTS = (
    "password",
    "passwd",
    "secret",
    "token",
    "api_key",
    "apikey",
    "authorization",
    "bearer",
    "credential",
)

# AsyncTaskSupervisor
SUPERVISOR_KEY_COPILOT_PREFIX = "copilot:"
MICRO_ROUTER_TIMEOUT_S = 3.5
MICRO_ROUTER_MIN_CONFIDENCE = 0.6
MICRO_ROUTER_MAX_TOKENS = 128
MICRO_ROUTER_SYSTEM_PROMPT = (
    "Classify the user message into exactly one intent. "
    "Reply with JSON only (no markdown): "
    '{"intent":"<chat_fast|research|test_scenario|test_case_gen|test_script_gen|'
    'exec_and_bind|swarm_audit>","confidence":0.0,"delivery_mode":"draft"|"apply"}. '
    "Use ThinkingLevel OFF — no chain-of-thought."
)
MICRO_ROUTER_USER_TEMPLATE = "User message:\n{prompt}\n\nContext keys: {context_keys}"
