# Bản Thiết Kế Tổng Thể: AI QA Test Studio Đa Nền Tảng (Master Blueprint v4.0)

Tài liệu này là bản thiết kế kiến trúc kỹ thuật và trải nghiệm sản phẩm toàn diện để xây dựng **AI QA Test Studio** trên nền tảng **Python đa hệ điều hành (Windows, macOS, Linux)**. Hệ thống kế thừa cấu trúc quản lý phân cấp của dự án gốc, giải quyết triệt để bài toán tràn output token của LLM, tích hợp hệ sinh thái **MCP (Model Context Protocol)** và **Modular Testing Skills**, hỗ trợ kiểm thử từ lúc **chưa có URL (Shift-Left)** đến khi **vận hành tự chủ (Autonomous Dual-Loop Agent & Self-Healing)**, đồng thời cung cấp giao diện trực quan cho cả người không biết lập trình.

---

## 1. Tầm Nhìn Sản Phẩm & Giải Quyết Triệt Để Điểm Nghẽn Cốt Lõi

### Chuyển dịch mô hình: Từ "Máy sinh văn bản" sang "Autonomous MCP-Powered QA Studio"

| Tiêu chí kỹ thuật & nghiệp vụ | Kiến trúc cũ (JavaFX One-Shot) | Kiến trúc mới (AI QA Test Studio v4.0) |
| :--- | :--- | :--- |
| **Cơ chế hoạt động của AI** | Đọc đề bài + DOM lớn $\rightarrow$ Cố gắng sinh trọn vẹn cả file kịch bản trong 1 lượt gọi API. | **Kiến trúc Dual-Loop (ReAct + Verification):** Quan sát màn hình rút gọn, gọi 1 micro-tool nhỏ mỗi lượt, tự ghi nhận step và kiểm chứng kết quả cuối. |
| **Kích thước Output / lượt** | $2.000 - 8.000+$ tokens (thường xuyên chạm trần `max_tokens`, đứt gãy chuỗi JSON). | $30 - 150$ tokens / lượt gọi tool (chỉ trả về tên hàm và mã phần tử ngắn gọn `[e1]`). |
| **Kiểm thử khi chưa có URL** | Bắt buộc phải có trang web đang chạy mới tạo được kịch bản. | **Hỗ trợ Shift-Left:** Sinh kịch bản ngữ nghĩa (`UNBOUND`) từ tài liệu SRS / Figma và tự động đồng bộ (`BOUND`) ngay khi có URL thật. |
| **Mở rộng kỹ năng & Công cụ** | Logic prompt và thao tác trình duyệt bị đóng cứng trong code Java. | **Plug-and-Play MCP + Living Skills:** Cắm nóng các MCP Server (`playwright`, `headroom`, `markitdown`, `midscene`, `figma`, `db`) và nạp động kỹ năng từ `SKILL.md`. |
| **Quản lý Locator & Bảo trì** | Lưu trùng lặp selector trong từng bước test, khó bảo trì khi UI đổi. | **Shared Element Repository + Hybrid Fallback:** Quản lý kho phần tử dùng chung, tự vá lỗi qua DOM + Vision Set-of-Mark. |
| **Quy trình & Báo cáo QA** | Chỉ lưu các bước thao tác kỹ thuật rời rạc. | Chuẩn hóa 6 tầng theo **ISO/IEC/IEEE 29119**, xuất báo cáo HTML tương tác (kèm Visual Action Replay) và Excel chuẩn **IEEE 829 + RTM**. |

---

## 2. Tổng Hợp Tinh Hoa Kiến Trúc Từ 13 Repository Nguồn (`knowledge/`)

Thay vì ghép nối cơ học toàn bộ mã nguồn của 13 dự án, kiến trúc **AI QA Test Studio v4.0** chắt lọc đúng năng lực lõi xuất sắc nhất của từng repository để lắp vào các phân hệ chuyên trách:

| Nhóm chức năng | Repository nguồn | Tinh hoa kỹ thuật được tích hợp vào hệ thống |
| :--- | :--- | :--- |
| **Nén ngữ cảnh & Nhập liệu** | **`headroom-main`** | Tích hợp `headroom-ai` làm lớp middleware chặn trước payload gửi lên LLM. `SmartCrusher` nén mảng DOM/JSON giảm $60 - 95\%$ token, `CacheAligner` bảo toàn tiền tố KV-cache, và lưu bản gốc cục bộ để Agent gọi `headroom_retrieve` khi cần soi chi tiết. |
| **Nén ngữ cảnh & Nhập liệu** | **`markitdown-main`** | Dùng `MarkItDown` làm bộ tiền xử lý tài liệu đầu vào: chuyển đổi file đặc tả SRS (`.pdf`, `.docx`, `.xlsx`, `.pptx`) thành Markdown sạch, giữ nguyên cấu trúc bảng trước khi sinh Test Case. |
| **Kiến trúc Skills & Bộ nhớ** | **`ECC-main`** | Chuẩn hóa gói kỹ năng theo thư mục `skills/<name>/SKILL.md` với cơ chế **Progressive Disclosure** (chỉ nạp tóm tắt vào System Prompt, đọc toàn văn khi kích hoạt) và **Pre/Post-Tool Hooks** (tự động chụp ảnh, che mật khẩu). |
| **Kiến trúc Skills & Bộ nhớ** | **`hermes-agent-main`** | Áp dụng mô hình **Living Skills & Sidecar Telemetry**: tách phần hướng dẫn tĩnh trong `SKILL.md` khỏi file thống kê thực chiến `.usage.json` (lưu đặc thù từng domain như Shadow DOM, iframe) để không phá vỡ Prompt Cache Prefix. |
| **Vòng lặp Agent & Điều phối** | **`loop-engineering-main`** | Triển khai mô hình **Dual-Loop**: *Inner Loop (ReAct)* thực thi từng bước nguyên tử; *Outer Loop (Ralph Verification Mode)* đối chiếu trạng thái cuối trang web với *Expected Result* trước khi chốt `PASS`. |
| **Vòng lặp Agent & Điều phối** | **`ruflo-main`** | Cơ chế **Multi-Agent Swarm Worker**: điều phối nhiều Agent con chạy song song trên các browser context độc lập khi thực thi bộ Regression Test lớn và gộp báo cáo tập trung. |
| **Persona & Micro-Skills** | **`agency-agents-main`** | Trích xuất bộ persona chuyên sâu (*QA Automation Engineer*, *API/Security Tester*, *Accessibility Auditor*) làm các **Agent Profile** cho phép người dùng chọn nhanh trên thanh công cụ. |
| **Persona & Micro-Skills** | **`Fabric-main`** | Chuyển hóa các pattern nguyên tử thành **Quick-Action Micro-Skills** một chạm trên UI: sinh test biên (`BVA & Equivalence Partitioning`), phân tích nguyên nhân lỗi (`analyze_logs`), kiểm tra bảo mật cơ bản (`threat_check`). |
| **Thực thi Hybrid & Quản lý** | **`midscene-main`** | Hỗ trợ định dạng kịch bản khai báo **YAML Test Script** dễ đọc cho người không biết code và cơ chế **Visual Action Replay** (chấm tọa độ thao tác lên ảnh chụp từng bước trong báo cáo HTML). |
| **Thực thi Hybrid & Quản lý** | **`skyvern-main`** | Bổ sung cơ chế **Set-of-Mark (Vision Bounding Boxes)** làm lớp định vị dự phòng khi gặp `<canvas>`, biểu đồ động hoặc UI tùy chỉnh mà CSS/XPath không bắt được. |
| **Thực thi Hybrid & Quản lý** | **`automa-main`** | Tái sử dụng mô hình **Block-based Workflow JSON** (`Trigger`, `Condition If/Else`, `Loop Data`, `Extract Table`) để biểu diễn luồng kịch bản trực quan trên giao diện PySide6. |
| **Thực thi Hybrid & Quản lý** | **`testsigma-dev`** | Áp dụng kiến trúc **Shared Element Repository** (tách kho locator dùng chung khỏi từng Test Case — sửa 1 nơi cập nhật toàn bộ Suite) và **Test Data Profiles** cho Data-Driven Testing. |
| **Thực thi Hybrid & Quản lý** | **`orca`** | Thiết kế UX **Embedded Browser + Inspector Design Mode**, cho phép QA click trực tiếp lên phần tử trên trình duyệt nhúng để ghim locator hoặc khoanh vùng yêu cầu Agent kiểm thử. |

---

## 3. Kiến Trúc Hệ Thống Đa Nền Tảng, MCP Host & Modular Skills

### Sơ đồ kiến trúc 5 tầng (Layered Architecture)

```text
+---------------------------------------------------------------------------------------+
| 1. PRESENTATION LAYER (PySide6 + qasync Unified Non-Blocking Event Loop)              |
|  [AI Test Wizard]  [Test Explorer]  [No-Code / YAML / Python Editor]  [Embedded View] |
+-------------------------------------------+-------------------------------------------+
                                            | Qt Signals / Async Coroutines
+-------------------------------------------v-------------------------------------------+
| 2. ORCHESTRATION & SKILL LAYER (ECC + Hermes + Agency-Agents + Loop-Engineering)      |
|  [Agent Persona Profile]   [Progressive Skill Loader (SKILL.md)]   [Dual-Loop Engine] |
|  (QA / Security / A11y)    (+ Sidecar .usage.json Cache-Safe)      (ReAct + Verify)   |
+-------------------------------------------+-------------------------------------------+
                                            | Compressed Context (Headroom + Tiktoken)
+-------------------------------------------v-------------------------------------------+
| 3. MCP HOST & TOOL GATEWAY (mcp Python SDK - Plug & Play Servers)                     |
|  [playwright-mcp]  [headroom-mcp]  [markitdown-mcp]  [midscene-mcp]  [figma/db-mcp]   |
+-------------------------------------------+-------------------------------------------+
                                            | Hybrid Locators (DOM [e1..en] + Vision)
+-------------------------------------------v-------------------------------------------+
| 4. SHARED REPOSITORY & SHIFT-LEFT ENGINE (Testsigma + Skyvern + Auto-Binder)          |
|  [Shared Element Repo]     [Shift-Left Auto-Binder]     [Self-Healing Micro-Patcher]  |
+-------------------------------------------+-------------------------------------------+
                                            | Pydantic v2 Models (JSON / SQLite)
+-------------------------------------------v-------------------------------------------+
| 5. ISO/IEEE 29119 STORAGE & REPORTING ENGINE                                          |
|  [Project & Data Store]    [Interactive HTML + Visual Replay]    [IEEE 829 Excel/RTM] |
+---------------------------------------------------------------------------------------+
```

### Bộ 6 MCP Servers Cốt Lõi & Cấu Hình `mcp_servers.json`

Ứng dụng đóng vai trò là **MCP Host** (sử dụng `mcp` Python SDK), tự động khởi chạy và nạp danh sách tool từ tệp cấu hình `mcp_servers.json`:

| MCP Server | Gói cài đặt / Lệnh | Vai trò trong AI QA Test Studio |
| :--- | :--- | :--- |
| **`playwright-mcp`** | `npx -y @playwright/mcp@latest` | **MCP điều khiển chính.** Thao tác trình duyệt qua **Accessibility Snapshot** (nhẹ hơn $70\%$ so với HTML thô), hỗ trợ click, fill, bắt network request, console error và multi-tab. |
| **`headroom-mcp`** | `uvx headroom-ai mcp` | **MCP tối ưu token.** Cung cấp `headroom_compress` và `headroom_retrieve` giúp nén lịch sử hội thoại/DOM và truy xuất lại dữ liệu gốc khi Agent cần kiểm tra sâu. |
| **`markitdown-mcp`** | `uvx markitdown-mcp` | **MCP đọc tài liệu.** Chuyển đổi trực tiếp file SRS (`.docx`, `.pdf`, `.xlsx`) trên máy người dùng thành Markdown chuẩn để sinh Test Case. |
| **`midscene-mcp`** | `npx -y @midscene/mcp` | **MCP thị giác dự phòng.** Cung cấp các tool `midscene_ai_locate`, `midscene_ai_assert` dựa trên ảnh chụp màn hình khi DOM bị che khuất hoặc dùng Canvas/Shadow DOM. |
| **`figma-mcp`** | Figma Dev Mode / MCP Server | **MCP Shift-Left.** Đọc cây thành phần giao diện, nhãn nút và luồng màn hình trực tiếp từ link thiết kế Figma để sinh **Draft Script (`UNBOUND`)** trước khi có code web. |
| **`db-mcp`** | `@modelcontextprotocol/server-postgres` / `sqlite` | **MCP kiểm chứng dữ liệu ngầm.** Cho phép Agent chạy truy vấn `SELECT` xác minh dữ liệu trong database ngay sau khi hoàn tất thao tác trên giao diện. |

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["-y", "@playwright/mcp@latest", "--isolated"]
    },
    "headroom": {
      "command": "uvx",
      "args": ["headroom-ai", "mcp"]
    },
    "markitdown": {
      "command": "uvx",
      "args": ["markitdown-mcp"]
    },
    "midscene": {
      "command": "npx",
      "args": ["-y", "@midscene/mcp"]
    }
  }
}
```

### Cấu Trúc Hệ Thống Testing Skills (`skills/`)

Thư mục `skills/` cho phép đội ngũ QA thêm mới hoặc chia sẻ các kỹ năng kiểm thử chuyên biệt mà không cần chỉnh sửa mã nguồn lõi:

```text
skills/
├── web-e2e-functional/
│   ├── SKILL.md               # Quy tắc kiểm thử luồng nghiệp vụ, xử lý popup/loading/toast
│   └── .usage.json            # Bộ nhớ tự học theo domain (cơ chế Hermes Living Skill)
├── boundary-value-analysis/
│   └── SKILL.md               # Quy tắc sinh test case BVA, phân vùng tương đương, SQLi/XSS
├── shift-left-figma-sync/
│   └── SKILL.md               # Quy tắc sinh script UNBOUND từ Figma/SRS & đồng bộ khi có URL
└── api-db-verification/
    └── SKILL.md               # Quy tắc gọi db-mcp kiểm tra tính toàn vẹn dữ liệu sau thao tác UI
```

#### Mã nguồn lõi Bộ Nạp Kỹ Năng & Quản Lý MCP (`src/agent/skill_mcp_manager.py`)

```python
import json
from pathlib import Path
from typing import Dict, Any, List
from pydantic import BaseModel

class SkillMetadata(BaseModel):
    name: str
    description: str
    skill_path: Path
    usage_path: Path

class ProgressiveSkillLoader:
    """Nạp metadata nhẹ vào System Prompt, chỉ đọc chi tiết SKILL.md khi kích hoạt (ECC + Hermes)"""
    def __init__(self, skills_dir: Path):
        self.skills_dir = skills_dir
        self.registry: Dict[str, SkillMetadata] = {}
        self._scan_skills()

    def _scan_skills(self):
        if not self.skills_dir.exists():
            return
        for folder in self.skills_dir.iterdir():
            skill_file = folder / "SKILL.md"
            if folder.is_dir() and skill_file.exists():
                first_lines = skill_file.read_text(encoding="utf-8").splitlines()[:5]
                desc = next((line.lstrip("# ").strip() for line in first_lines if line.strip()), folder.name)
                self.registry[folder.name] = SkillMetadata(
                    name=folder.name,
                    description=desc,
                    skill_path=skill_file,
                    usage_path=folder / ".usage.json"
                )

    def get_compact_catalog(self) -> str:
        """Chỉ tốn ~15 tokens mỗi skill trong System Prompt"""
        lines = ["AVAILABLE TESTING SKILLS (Gọi tool `load_skill` để xem hướng dẫn chi tiết):"]
        for name, meta in self.registry.items():
            lines.append(f"- {name}: {meta.description}")
        return "\n".join(lines)

    def load_skill_context(self, skill_name: str, domain: str = "") -> str:
        meta = self.registry.get(skill_name)
        if not meta:
            return f"Skill '{skill_name}' không tồn tại."
        content = meta.skill_path.read_text(encoding="utf-8")
        # Nạp thêm ghi chú thực chiến của domain từ .usage.json (nếu có)
        if domain and meta.usage_path.exists():
            usage_data = json.loads(meta.usage_path.read_text(encoding="utf-8"))
            domain_notes = usage_data.get("domains", {}).get(domain, [])
            if domain_notes:
                content += "\n\n### Kinh nghiệm thực chiến trên domain này:\n" + "\n".join(f"- {n}" for n in domain_notes)
        return content
```

---

## 4. Mô Hình Dữ Liệu Chuẩn Quốc Tế (ISO/IEC/IEEE 29119) & Shared Element Repository

Hệ thống mở rộng cấu trúc phân cấp `Group / Suite / Case` ban đầu thành **6 tầng chuẩn doanh nghiệp**, kết hợp **Shared Element Repository** (`testsigma-dev`) để tách biệt định nghĩa phần tử giao diện khỏi từng bước kịch bản:

```text
Project (Dự án & Cấu hình Môi trường: DEV / STAGING / PROD)
 ├── Shared Element Repository (Kho định vị phần tử dùng chung toàn dự án)
 └── Test Group (Phân hệ chức năng, VD: Quản lý Thanh toán)
      └── Test Scenario (Kịch bản kiểm thử cấp cao - High-level flow)
           └── Test Suite (Bộ chạy theo đợt: Smoke, Regression, Release)
                └── Test Case (Ca kiểm thử chuẩn ISO 29119: Precondition, Data, Expected)
                     └── Test Script (Chuỗi TestStep hỗ trợ UNBOUND -> BOUND -> HEALED)
```

### Schema Dữ Liệu Lõi (`src/core/models.py`)

```python
from typing import Literal, Optional, Any
from pydantic import BaseModel, Field

class SharedElement(BaseModel):
    """Phần tử giao diện dùng chung toàn dự án (mô hình Testsigma Shared Repository)"""
    element_id: str = Field(..., description="Mã định danh duy nhất, VD: EL_LOGIN_SUBMIT")
    page_name: str = Field(..., description="Tên màn hình, VD: LoginPage")
    friendly_name: str = Field(..., description="Tên hiển thị cho QA, VD: Nút Đăng nhập")
    primary_playwright: Optional[str] = Field(
        None, description="Locator hướng người dùng: get_by_role('button', name='Đăng nhập')"
    )
    test_id: Optional[str] = None
    role: Optional[str] = None
    name: Optional[str] = None
    placeholder: Optional[str] = None
    css: Optional[str] = None
    xpath: Optional[str] = None
    vision_prompt: Optional[str] = Field(None, description="Mô tả thị giác dự phòng cho Midscene/Skyvern")
    thumbnail_b64: Optional[str] = Field(None, description="Ảnh chụp nhỏ phần tử hiển thị trên UI")

class TestStep(BaseModel):
    step_no: int
    action: Literal[
        "goto", "click", "fill", "select", "press_enter",
        "hover", "assert_visible", "assert_text", "assert_url", "verify_db"
    ]
    element_ref: Optional[str] = Field(None, description="Tham chiếu tới SharedElement.element_id")
    intent_hint: str = Field(..., description="Mô tả ngữ nghĩa bước để AI đối chiếu khi Auto-Bind")
    input_data: Optional[str] = Field(None, description="Giá trị nhập hoặc biến, VD: '{{user_email}}'")
    expected_result: str = Field("", description="Kết quả mong đợi của riêng bước này")
    binding_status: Literal["UNBOUND", "BOUND", "HEALED"] = "UNBOUND"

class TestCase(BaseModel):
    id: str
    code: str = Field(..., description="Mã chuẩn QA, VD: TC_AUTH_001")
    title: str
    priority: Literal["P0 - Critical", "P1 - High", "P2 - Medium", "P3 - Low"] = "P1 - High"
    test_type: Literal["Functional", "Negative", "Boundary", "UI/UX", "Security"] = "Functional"
    preconditions: list[str] = Field(default_factory=list)
    test_data_rows: list[dict[str, Any]] = Field(default_factory=list, description="Dữ liệu Data-Driven")
    expected_final_result: str = ""
    steps: list[TestStep] = Field(default_factory=list)
    last_status: Literal["NOT_RUN", "PASS", "FAIL", "HEALED"] = "NOT_RUN"

class TestScenario(BaseModel):
    id: str
    code: str = Field(..., description="VD: TS_AUTH_01")
    title: str
    requirement_ref: str = Field("", description="Mã User Story / SRS liên kết")
    description: str = ""
    cases: list[TestCase] = Field(default_factory=list)

class TestGroup(BaseModel):
    id: str
    name: str
    scenarios: list[TestScenario] = Field(default_factory=list)
```

---

## 5. Quy Trình AI 4 Giai Đoạn, Shift-Left Testing & Kiểm Soát Token Tuyệt Đối

### Giai đoạn A: Nhập Liệu Đa Định Dạng & Sinh Khung Scenario (`Outline-First`)
1. **Chuyển đổi tài liệu bằng `markitdown-mcp`:** Khi QA tải lên file đặc tả (`.docx`, `.pdf`, `.xlsx`), hệ thống tự động trích xuất thành Markdown sạch giữ nguyên cấu trúc bảng yêu cầu.
2. **Sinh khung Scenario tiết kiệm token:** AI chỉ xuất ra danh sách các luồng nghiệp vụ chính (Happy Path, Negative, Edge Cases) gồm `code`, `title`, `requirement_ref`, `description` ($\approx 300 - 600$ tokens cho $10 - 15$ Scenarios). Người dùng tick chọn các Scenario cần triển khai.

### Giai đoạn B: Sinh Chi Tiết Test Case Chuẩn IEEE 829 (`Chunked Batch`)
* Hệ thống kích hoạt skill `boundary-value-analysis` (tích hợp kỹ thuật từ `Fabric-main`), gọi API theo từng lô nhỏ ($2 - 3$ Test Case / lượt) để sinh đầy đủ `preconditions`, `test_data_rows` (dữ liệu biên, dữ liệu lỗi) và các bước thực hiện ($\approx 500 - 900$ tokens / lượt).

### Giai đoạn C: Tạo Script Trước Khi Có URL & Tự Động Đồng Bộ Khi Có URL (`Shift-Left Auto-Binding`)

Quy trình giúp đội QA hoàn thiện $80 - 90\%$ bộ automation script song song với lúc Dev đang code giao diện:

1. **Khi chưa có URL (Sinh Semantic Draft Script — trạng thái `UNBOUND`):**
   * AI đọc `TestCase` kết hợp tài liệu SRS hoặc thiết kế từ `figma-mcp`, tạo ra các `SharedElement` và `TestStep` ở trạng thái `UNBOUND`.
   * Thay vì đoán mò CSS/XPath, AI sinh locator hướng người dùng (`get_by_role("button", name="Thanh toán")`, `get_by_label("Email")`) kèm theo `intent_hint` mô tả vai trò ngữ nghĩa.
2. **Khi có URL thật (Chạy `Auto-Binding` 3 tầng để chuyển sang `BOUND`):**
   * **Tầng 1 — Zero-Token Semantic Match:** Playwright mở URL thật và kiểm tra trực tiếp `primary_playwright`. Nếu khớp phần tử duy nhất trên trang, hệ thống tự động thu thập thêm `css`, `xpath`, `test_id`, chụp ảnh thumbnail và chuyển trạng thái sang `BOUND` mà **không tốn** $1$ **token AI nào**.
   * **Tầng 2 — AI DOM Grounding (Vá cục bộ bước sai lệch):** Chỉ với các bước còn ở trạng thái `UNBOUND` (do tên nút trên web thật khác với bản thiết kế), Agent đọc cây Accessibility rút gọn `[e1], [e2]...`, đối chiếu với `intent_hint` và gọi micro-tool `bind_step_element(step_no, element_id)` ($\approx 40$ tokens/bước).
   * **Tầng 3 — Verification Run:** Chạy nghiệm thu toàn bộ kịch bản trên URL thật để xác nhận luồng thông suốt.

#### Mã nguồn lõi Bộ Đồng Bộ Shift-Left (`src/agent/auto_binder.py`)

```python
class ShiftLeftAutoBinder:
    """Tự động đồng bộ kịch bản UNBOUND với URL thật bằng cơ chế 2 tầng tiết kiệm token"""
    def __init__(self, page, llm_client, dom_distiller, locator_resolver, element_repo):
        self.page = page
        self.llm = llm_client
        self.distiller = dom_distiller
        self.resolver = locator_resolver
        self.element_repo = element_repo

    async def sync_test_case(self, test_case, base_url: str, on_progress):
        await self.page.goto(base_url, wait_until="networkidle")

        for step in test_case.steps:
            if step.action in ("goto", "verify_db"):
                step.binding_status = "BOUND"
                continue

            shared_el = self.element_repo.get(step.element_ref)
            if not shared_el:
                continue

            # TẦNG 1: Zero-Token Verification (Khớp trực tiếp qua User-Facing Locator)
            matched_handle = await self.resolver.try_semantic_match(self.page, shared_el)
            if matched_handle:
                await self.resolver.enrich_element_bundle(matched_handle, shared_el)
                step.binding_status = "BOUND"
                on_progress(f"Bước {step.step_no}: Khớp tự động (0 token) -> BOUND")
                await self.resolver.execute_step(self.page, step, shared_el)
                continue

            # TẦNG 2: AI DOM Grounding (Chỉ gọi AI cho những bước sai lệch với thiết kế)
            compact_dom, element_map = await self.distiller.extract(self.page)
            decision = await self.llm.call_single_tool(
                tool_name="bind_step_element",
                prompt=f"Tìm phần tử khớp với ý định: '{step.intent_hint}' (Hành động: {step.action})",
                current_dom=compact_dom,
                max_tokens=200  # Output cực nhỏ (~40 tokens)
            )

            if decision and decision.args.get("element_id") in element_map:
                target_meta = element_map[decision.args["element_id"]]
                await self.resolver.update_element_from_meta(self.page, target_meta, shared_el)
                step.binding_status = "BOUND"
                on_progress(f"Bước {step.step_no}: AI đồng bộ sang [{decision.args['element_id']}] -> BOUND")

            await self.resolver.execute_step(self.page, step, shared_el)
```

### Giai đoạn D: 5 Lớp Kiểm Soát Token Tuyệt Đối (Triệt Tiêu Lỗi Vượt Output Token)

1. **Rút gọn DOM tương tác & Đánh chỉ mục ngắn (`DOMDistiller`):** Lọc bỏ thẻ tĩnh (`div` bọc ngoài, `svg`, `script`, `style`, node ẩn), gán mã `[e1]`, `[e2]` cho phần tử tương tác — giảm từ $35.000$ tokens xuống còn $600 - 1.200$ tokens. LLM chỉ trả về `"element_id": "e14"` ($4$ tokens).
2. **Lớp nén ngữ cảnh `headroom` Middleware:** Tự động nén các mảng dữ liệu lớn hoặc log dài trước khi đẩy vào LLM, đồng thời giữ nguyên tiền tố Prompt Cache để tăng tốc độ phản hồi và giảm chi phí API.
3. **Cửa sổ ngữ cảnh trượt (`Sliding Context Window` + `tiktoken`):** Chỉ giữ lại duy nhất DOM snapshot của bước hiện tại; toàn bộ DOM của các bước trước được nén thành tóm tắt $1$ dòng trong lịch sử.
4. **Atomic Tool Calling (`max_tokens=512`):** Ép LLM chỉ gọi duy nhất $1$ micro-tool mỗi lượt (`navigate`, `interact`, `assert`, `bind_step_element`, `patch_element_locator`, `load_skill`, hoặc `finish_task`).
5. **Cơ chế nối tiếp tự động (`Auto-Continuation`):** Khi xuất file code Python lớn cho cả Test Suite và gặp `finish_reason == "length"`, hệ thống tự động đưa phần code dở dang vào role `assistant` và yêu cầu viết tiếp chính xác từ ký tự cuối cùng.

---

## 6. Thiết Kế UX/UI "Zero-Friction IDE" Cho Mọi Đối Tượng (PySide6 + `qasync`)

Giao diện tuân thủ nguyên tắc **Progressive Disclosure**: Tester thủ công làm việc hoàn toàn bằng thao tác trực quan không cần viết code; kỹ sư Automation có thể chuyển sang tab YAML (`midscene-main`) hoặc Python Code (`pytest-playwright`) bất cứ lúc nào.

### Bố Cục Không Gian Làm Việc 4 Vùng (Adaptive 4-Pane Workspace)

```text
+-----------------------------------------------------------------------------------------------------------+
| [Dự án: E-Commerce] | Persona: [🛡️ QA Engineer v] | Môi trường: [Staging v] | [✨ Wizard] [🔗 Sync URL]   |
+-----------------------+---------------------------------------------------+-------------------------------+
|  1. TEST EXPLORER     |  2. WORKSPACE EDITOR (Đa Tab Đồng Bộ 2 Chiều)     |  3. EMBEDDED BROWSER & AGENT  |
|  [Tìm kiếm / Lọc...]  |  [📋 Spec Chuẩn] [🧩 No-Code] [📄 YAML] [🐍 Code] |  [🌐 Live Browser] [🤖 Logs]  |
|                       |                                                   |                               |
|  ▼ 📁 Thanh Toán      |  Mã: TC_PAY_001 | Trạng thái: [🟢 BOUND]          |  +-------------------------+  |
|    ▼ 📘 TS_PAY_01     |  Ưu tiên: [P0 - Critical v] | Loại: Functional    |  |   Trình duyệt nhúng     |  |
|      🟢 TC_PAY_001    |  Điều kiện tiên quyết:                            |  |   [🎯 Inspector Mode]     |  |
|      🟡 TC_PAY_002    |   • Người dùng đã đăng nhập & có hàng trong giỏ   |  |   (Khoanh vùng phần tử)  |  |
|         (Unbound)     |                                                   |  +-------------------------+  |
|      ⚡ TC_PAY_003    |  BẢNG BƯỚC KIỂM THỬ (Kéo thả sắp xếp):           |                               |
|         (Healed)      |  # | Hành động | Đối tượng        | Dữ liệu     |  Luồng suy luận Agent:        |
|                       |  1 | Mở trang  | Trang thanh toán | /checkout   |  ✓ Nạp skill: web-e2e         |
|  📦 Shared Elements   |  2 | Nhập chữ  | [🖼️] Ô Email     | {{email}}   |  ✓ Khớp 5/6 bước (0 token)    |
|   • EL_PAY_EMAIL      |  3 | Click     | [🖼️] Nút Mua     | -           |  ⚡ Bước 6: AI tự cập nhật    |
|   • EL_PAY_SUBMIT     |  4 | Kiểm tra  | Thông báo        | "Đặt xong"  |     nút "Đặt hàng" -> BOUND   |
+-----------------------+---------------------------------------------------+-------------------------------+
|  4. BOTTOM DRAWER: [📊 Test Data Grid (CSV/Excel)]   [📸 Ảnh chụp & Trace]   [🔌 Trạng thái MCP Servers]  |
+-----------------------------------------------------------------------------------------------------------+
```

### 8 Điểm Nhấn UX/UI Giúp Người Dùng Dễ Sử Dụng & Quản Lý Hiệu Quả

1. **AI Test Wizard 3 Bước (Hỗ trợ cả Có URL & Chưa có URL):** Dẫn dắt người dùng từ nhập tài liệu SRS / link Figma / URL $\rightarrow$ chọn Test Scenario $\rightarrow$ duyệt Test Case chuẩn ISO $\rightarrow$ sinh Draft Script (`UNBOUND`) hoặc chạy quay tự động (`BOUND`).
2. **Nút "🔗 Sync & Bind URL" 1-Click:** Chuyển đổi hàng loạt kịch bản nháp `🟡 UNBOUND` sang `🟢 BOUND` ngay khi có link Staging, hiển thị rõ bước nào khớp miễn phí (0 token) và bước nào được AI tự động điều chỉnh.
3. **Trình Soạn Thảo 4 Chế Độ Đồng Bộ Hai Chiều:** Chuyển đổi mượt mà giữa **Spec Chuẩn ISO**, **No-Code Visual Steps** (kéo-thả đổi thứ tự), **YAML Script** (chuẩn `midscene-main`), và **Python Pytest Code**.
4. **Embedded Browser & Point-and-Click Inspector (`orca`):** Cho phép mở trình duyệt ngay bên trong ứng dụng, bật chế độ ống ngắm `🎯` để click vào nút bấm trên web và tự động thêm vào **Shared Element Repository**.
5. **Visual Element Thumbnail & Live Highlighting:** Hiển thị ảnh chụp thu nhỏ của từng nút bấm ngay trong bảng bước test và tự động khoanh viền phát sáng trên trình duyệt khi người dùng chọn dòng tương ứng.
6. **Agent Persona & Skill Switcher (`agency-agents-main`):** Cho phép chọn nhanh vai trò AI (*Functional QA*, *Security & Boundary Tester*, *Accessibility Auditor*) trước khi chạy để Agent tự động kích hoạt bộ quy tắc kiểm thử phù hợp.
7. **Bảng Dữ Liệu Kiểm Thử Tích Hợp (Data-Driven Testing Grid):** Nhập dữ liệu test theo dạng bảng hoặc import CSV/Excel, sử dụng biến `{{ten_cot}}` để tự động chạy lặp qua nhiều bộ dữ liệu.
8. **Bảng Trạng Thái MCP Servers & Command Palette (`Ctrl + K` / `Cmd + K`):** Theo dõi trạng thái kết nối của các MCP Server (`playwright`, `headroom`, `markitdown`, `midscene`, `db`) và tìm kiếm nhanh mọi Test Case bằng phím tắt.

---

## 7. Hệ Thống Xuất Báo Cáo & Tài Liệu Chuẩn Doanh Nghiệp

1. **Báo Cáo Thực Thi HTML Tương Tác Độc Lập (Self-Contained HTML Report):**
   * Đóng gói thành **1 file `.html` duy nhất** (nhúng CSS, biểu đồ SVG, ảnh chụp màn hình Base64) để gửi qua Email, Slack, Teams hoặc đính kèm Jira.
   * Tích hợp **Visual Action Replay** (`midscene-main`): hiển thị ảnh chụp màn hình từng bước có đánh dấu chính xác tọa độ click/nhập liệu, thời gian phản hồi (ms), bằng chứng ảnh khi lỗi, và bảng đối chiếu **AI Self-Healing & Auto-Binding Audit Log** (`Locator cũ` vs `Locator mới`).
2. **Bộ Tài Liệu Kiểm Thử Excel (`.xlsx`) Chuẩn IEEE 829:**
   * Xuất tự động bằng `openpyxl` gồm **4 Sheet liên kết**:
     * **Sheet 1 — `Overview & RTM`:** Ma trận truy xuất nguồn gốc (`Requirement ID` $\rightarrow$ `Test Scenario` $\rightarrow$ `Test Case` $\rightarrow$ `Trạng thái thực thi`).
     * **Sheet 2 — `Test Scenarios`:** Danh sách kịch bản cấp cao phân theo phân hệ.
     * **Sheet 3 — `Test Cases (IEEE 829)`:** Đầy đủ `Test Case ID`, `Scenario ID`, `Title`, `Priority`, `Test Type`, `Pre-conditions`, `Step No`, `Action`, `Test Data`, `Expected Result`, `Actual Result`, `Binding Status`, `Execution Status`.
     * **Sheet 4 — `Defects & AI Insights`:** Tổng hợp bước lỗi kèm phân tích nguyên nhân gốc rễ (Root Cause Suggestion) do AI đề xuất.
3. **Xuất Dự Án Mã Nguồn Độc Lập Cho CI/CD (`Export Pytest Suite`):**
   * Xuất toàn bộ Test Suite và **Shared Element Repository** thành dự án Python độc lập chuẩn `pytest-playwright` kèm file `.github/workflows/qa-automation.yml` để tích hợp ngay vào GitHub Actions, GitLab CI hoặc Jenkins.

---

## 8. Cấu Trúc Thư Mục, Đóng Gói Đa Hệ Điều Hành & Lộ Trình Triển Khai

### Cấu trúc mã nguồn dự án (`ai-qa-test-studio`)

```text
ai-qa-test-studio/
├── pyproject.toml                  # Quản lý dependency chuẩn bằng uv
├── mcp_servers.json                # Cấu hình các MCP Server cắm nóng
├── skills/                         # Kho Modular Testing Skills (ECC + Hermes)
│   ├── web-e2e-functional/
│   │   ├── SKILL.md
│   │   └── .usage.json
│   ├── boundary-value-analysis/
│   │   └── SKILL.md
│   ├── shift-left-figma-sync/
│   │   └── SKILL.md
│   └── api-db-verification/
│       └── SKILL.md
├── src/
│   ├── main.py                     # Khởi tạo QApplication & qasync Unified Event Loop
│   ├── core/
│   │   ├── models.py               # Pydantic Models chuẩn ISO 29119 & SharedElement
│   │   ├── storage.py              # Lưu trữ JSON/SQLite + Import dữ liệu từ repo Java cũ
│   │   ├── env_manager.py          # Quản lý biến môi trường (Dev/Staging/Prod) & Data-Driven
│   │   └── token_budget.py         # Đếm token chuẩn tiktoken & tích hợp Headroom Compressor
│   ├── browser/
│   │   ├── engine.py               # Quản lý Playwright Browser, Context, Page
│   │   ├── dom_distiller.py        # Lọc cây DOM tương tác & đánh chỉ mục [e1], [e2]...
│   │   ├── locator_resolver.py     # Chạy Multi-Strategy Locator & Fallback không tốn token
│   │   └── visual_inspector.py     # Khoanh vùng highlight phần tử, chụp thumbnail & tọa độ
│   ├── agent/
│   │   ├── mcp_manager.py          # MCP Host kết nối & gọi tool từ các MCP Server
│   │   ├── skill_loader.py         # Nạp động SKILL.md & cập nhật .usage.json
│   │   ├── spec_generator.py       # AI sinh Scenario (Outline) & Test Case (Batch)
│   │   ├── auto_binder.py          # Shift-Left Engine: Đồng bộ UNBOUND -> BOUND khi có URL
│   │   ├── dual_loop_agent.py      # ReAct Execution Loop + Ralph Verification Loop
│   │   └── healer.py               # Self-Healing Agent vá cục bộ (DOM + Midscene Vision)
│   ├── reporting/
│   │   ├── html_reporter.py        # Xuất báo cáo HTML tương tác kèm Visual Action Replay
│   │   ├── excel_exporter.py       # Xuất tài liệu chuẩn IEEE 829 & RTM ra file .xlsx
│   │   └── code_exporter.py        # Xuất mã nguồn Pytest + Shared Repo + CI/CD pipeline
│   └── ui/
│       ├── theme.py                # Giao diện Dark/Light Mode hiện đại chuẩn QSS
│       ├── main_window.py          # Bố cục 4 vùng (Toolbar, Explorer, Editor, Bottom Drawer)
│       ├── wizards/
│       │   └── ai_test_wizard.py   # Trình hướng dẫn tạo Scenario -> Case -> Script 3 bước
│       └── widgets/
│           ├── test_explorer.py    # Cây thư mục phân cấp & Shared Element Repository
│           ├── step_builder.py     # Trình soạn thảo 4 chế độ (Spec / No-Code / YAML / Code)
│           ├── data_grid.py        # Bảng nhập Test Data cho Data-Driven Testing
│           └── embedded_browser.py # Khung trình duyệt nhúng & Inspector Mode
└── tests/
```

### Cấu hình `pyproject.toml` & Đóng gói đa nền tảng (Windows, macOS, Linux)

```toml
[project]
name = "ai-qa-test-studio"
version = "4.0.0"
description = "Autonomous Shift-Left & MCP-Powered AI QA Test Studio"
requires-python = ">=3.11"
dependencies = [
    "pyside6>=6.7.0",
    "qasync>=0.27.1",
    "playwright>=1.48.0",
    "mcp>=1.2.0",
    "headroom-ai>=0.1.0",
    "markitdown>=0.1.0",
    "pydantic>=2.9.0",
    "tiktoken>=0.8.0",
    "pyyaml>=6.0.2",
    "openpyxl>=3.1.5",
    "jinja2>=3.1.4",
    "openai>=1.50.0",
    "google-genai>=0.5.0"
]

[project.optional-dependencies]
build = ["pyinstaller>=6.10.0"]
```

* **Trải nghiệm khởi động lần đầu (Zero-Config First Run):** Khi mở ứng dụng lần đầu trên bất kỳ hệ điều hành nào, `main.py` tự động kiểm tra nhân trình duyệt Playwright và hiển thị thanh tiến trình tải Chromium nếu chưa có (`python -m playwright install chromium`), giúp bộ cài đặt gọn nhẹ ($\approx 65\text{ MB}$).
* **Lệnh đóng gói tự động cho từng hệ điều hành:**
  * **Windows (`.exe`):** `uv run pyinstaller --noconfirm --windowed --name "AIQATestStudio" src/main.py`
  * **macOS (`.app` chạy chuẩn Intel & Apple Silicon):** `uv run pyinstaller --noconfirm --windowed --name "AIQATestStudio" --osx-bundle-identifier "com.autotest.studio" src/main.py`
  * **Linux (Binary / AppImage):** `uv run pyinstaller --noconfirm --windowed --name "AIQATestStudio" src/main.py`

### Lộ Trình Triển Khai 5 Giai Đoạn

1. **Giai đoạn 1 — Core Models & Data Migration (2 ngày):** Khởi tạo dự án với `uv`, xây dựng `models.py` (hỗ trợ `SharedElement` và `UNBOUND/BOUND`) và viết script import dữ liệu JSON từ repo Java cũ.
2. **Giai đoạn 2 — MCP Host, Skills & Token Governance (3 ngày):** Triển khai `MCPClientManager`, `ProgressiveSkillLoader` (`SKILL.md` + `.usage.json`), tích hợp `headroom` và `tiktoken` để khóa ngân sách token.
3. **Giai đoạn 3 — Dual-Loop Agent & Shift-Left Auto-Binder (4 ngày):** Hoàn thiện vòng lặp ReAct + Verification, bộ đồng bộ `ShiftLeftAutoBinder` 2 tầng và cơ chế Self-Healing kết hợp DOM + `midscene-mcp`.
4. **Giai đoạn 4 — Giao diện PySide6 + `qasync` 4 Vùng (4 ngày):** Xây dựng `AI Test Wizard`, trình soạn thảo 4 chế độ đồng bộ hai chiều, trình duyệt nhúng kèm Inspector Mode và bảng Test Data.
5. **Giai đoạn 5 — Enterprise Reporting & CI/CD Packaging (2 ngày):** Hoàn thiện bộ xuất báo cáo HTML tương tác (Visual Action Replay), file Excel chuẩn IEEE 829 + RTM và cấu hình GitHub Actions build bộ cài cho Windows, macOS, Linux.