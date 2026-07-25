# Hướng dẫn sử dụng — Playwright Automation Studio

> Ứng dụng desktop JavaFX giúp quản lý và tự động hóa kiểm thử web bằng Playwright — không cần viết code thủ công.

---

## Mục lục

1. [Giới thiệu](#1-giới-thiệu)
2. [Giao diện tổng quan](#2-giao-diện-tổng-quan)
3. [Quản lý cây dự án — Explorer](#3-quản-lý-cây-dự-án--explorer)
4. [Tạo & sửa Nhóm / Suite / Case](#4-tạo--sửa-nhóm--suite--case)
5. [Workspace — Ghi hình & Chạy test](#5-workspace--ghi-hình--chạy-test)
6. [Xây dựng Assertion](#6-xây-dựng-assertion)
7. [Chạy test & Báo cáo](#7-chạy-test--báo-cáo)
8. [Xuất Excel](#8-xuất-excel)
9. [Cấu hình ứng dụng](#9-cấu-hình-ứng-dụng)
10. [Phím tắt & Mẹo](#10-phím-tắt--mẹo)

---

## 1. Giới thiệu

**Playwright Automation Studio** cho phép bạn:

- **Ghi hình** thao tác trên trình duyệt bằng Playwright Codegen — tự động sinh code Java
- **Quản lý test** theo cây 3 cấp: Nhóm (Group) → Bộ (Suite) → Ca (Case)
- **Thêm assertion** bằng giao diện trực quan — 46 loại assertion, 8 nhóm chức năng
- **Chạy test** với trình duyệt chia sẻ, chế độ ẩn, dừng khi lỗi đầu tiên
- **Xem báo cáo** chi tiết từng lần chạy: đạt/thất bại/bỏ qua, ảnh chụp màn hình
- **Xuất Excel** 3 sheet: Tổng quan, Cases, Assertions

Ứng dụng chạy test **ngay trong JVM** — không cần terminal, không cần cài đặt riêng Playwright runtime.

### Luồng làm việc tổng thể

```mermaid
flowchart LR
    A["🏗️ Tạo dự án<br/>Group → Suite → Case"] --> B["🔴 Ghi hình<br/>Codegen sinh script"]
    B --> C["✅ Thêm Assertion<br/>46 loại, 8 nhóm"]
    C --> D["▶️ Chạy test<br/>4 phạm vi"]
    D --> E["📊 Xem báo cáo<br/>Pass/Fail/Skip + ảnh"]
    E --> F["📥 Xuất Excel"]
    E -.->|"Sửa script"| B
    E -.->|"Thêm assertion"| C
```

---

## 2. Giao diện tổng quan

Giao diện chia làm 3 vùng chính:

```mermaid
flowchart LR
    subgraph Sidebar["Thanh bên (trái)"]
        Nav["🧭 Khám phá<br/>📊 Báo cáo"]
        SuiteInfo["📌 SUITE ĐANG MỞ"]
        RunAll["▶️ Chạy tất cả"]
    end
    
    subgraph Content["Vùng nội dung (giữa)"]
        Explorer["Trang Khám phá<br/>Cây Nhóm → Suite → Case"]
        Workspace["Trang Workspace<br/>Code + Record + Run"]
        Reports["Trang Báo cáo<br/>Lịch sử chạy"]
    end
    
    subgraph Overlay["Lớp phủ"]
        Toast["Thông báo Toast<br/>(góc trên phải)"]
        Help["Ngăn trợ giúp<br/>(bên phải)"]
    end
```

### Thanh bên

- **🧭 Khám phá**: Xem cây dự án, tạo/sửa/xóa Group/Suite/Case
- **📊 Báo cáo**: Xem lịch sử các lần chạy test
- **▶️ Chạy tất cả**: Chạy toàn bộ project
- **Thu gọn**: Nhấn nút toggle để thu sidebar còn 56px (chỉ hiện icon)

### Thanh trạng thái (dưới cùng)

Hiển thị trạng thái hiện tại: "Sẵn sàng", "Đang chạy…", "Đang ghi hình…", trình duyệt đang dùng.

---

## 3. Quản lý cây dự án — Explorer

Trang Khám phá hiển thị cây thư mục **Nhóm → Suite → Case**:

```mermaid
flowchart LR
    subgraph Tree["Cây dự án"]
        direction TB
        Root["📦 Dự án (gốc)"]
        Smoke["📁 Smoke<br/>group.json"]
        LoginFlow["📋 LoginFlow<br/>suite.json"]
        HomePage["📋 HomePage<br/>suite.json"]
        LoginOk["🧪 LoginOk<br/>LoginOk.java"]
        LoginWrong["🧪 LoginWrong<br/>LoginWrong.java"]
        OpenHome["🧪 OpenHome<br/>OpenHome.java"]
        Regression["📁 Regression<br/>group.json"]
    end
    
    Root --> Smoke
    Root --> Regression
    Smoke --> LoginFlow
    Smoke --> HomePage
    LoginFlow --> LoginOk
    LoginFlow --> LoginWrong
    HomePage --> OpenHome
    
    style Root fill:#9b59b6,color:#fff
    style Smoke fill:#3498db,color:#fff
    style Regression fill:#3498db,color:#fff
    style LoginFlow fill:#2ecc71,color:#fff
    style HomePage fill:#2ecc71,color:#fff
    style LoginOk fill:#f39c12,color:#fff
    style LoginWrong fill:#f39c12,color:#fff
    style OpenHome fill:#f39c12,color:#fff
```

### Thao tác trên cây

| Hành động | Cách làm |
|-----------|----------|
| **Chọn** | Click trái vào hàng |
| **Bỏ chọn** | Click lại vào hàng đã chọn |
| **Mở workspace** | Double-click vào Suite hoặc Case |
| **Menu chuột phải** | Click phải vào hàng (không đổi lựa chọn) |
| **Menu vùng trống** | Click phải vào khoảng trống trên cây |
| **Kéo thả** | Kéo Suite sang Nhóm khác để di chuyển; kéo Case để sắp xếp lại trong Suite |

### Panel Xem trước (bên phải)

Khi chọn một node trên cây, panel bên phải hiển thị:

| Node | Hiển thị |
|------|----------|
| **Dự án (gốc)** | Số lượng Nhóm / Suite / Case toàn dự án |
| **Nhóm** | Tên, mô tả, thống kê (Nhóm con, Suite, Case) |
| **Suite** | Tên, mô tả, trạng thái, trình duyệt, headless, ngày tạo/sửa, nút Sửa / Chạy / Mở |
| **Case** | Tên, mô tả, trạng thái, URL, trình duyệt, headless, ngày tạo/sửa, nút Sửa / Chạy / Mở |

Nút **✎ Sửa** mở form chỉnh sửa metadata (mô tả, trạng thái, URL, trình duyệt, headless). Nút **💾 Lưu** ghi thay đổi vào `suite.json`.

### Bộ lọc trạng thái

Combo box phía trên cây cho phép lọc theo trạng thái:

- **📋 Tất cả** — hiện mọi node
- **🟢 Đang hoạt động** — chỉ Suite ACTIVE / Case Ready
- **📝 Bản nháp** — Suite DRAFT / Case Draft
- **⏸️ Không hoạt động** — Suite INACTIVE

---

## 4. Tạo & sửa Nhóm / Suite / Case

### Tạo Nhóm

1. Chọn Nhóm cha (hoặc để trống để tạo ở gốc)
2. Nhấn **📁 Nhóm mới** trên toolbar (hoặc chuột phải → Nhóm mới)
3. Điền **Tên nhóm** (bắt buộc) và Mô tả (tùy chọn)
4. Nhấn **✅ Tạo**

### Tạo Suite

1. Chọn Nhóm cha
2. Nhấn **📋 Suite mới** trên toolbar (hoặc chuột phải → Suite mới)
3. Điền form:
   - **Tên suite** (bắt buộc)
   - **Mô tả**
   - **Trạng thái**: Bản nháp (DRAFT) / Đang hoạt động (ACTIVE) / Không hoạt động (INACTIVE)
   - **Trình duyệt**: «Mặc định (Playwright Chromium)» hoặc chọn trình duyệt cụ thể
   - **🙈 Ẩn trình duyệt**: chạy headless
4. Nhấn **✅ Tạo**

### Tạo Case

1. Chọn Suite cha (trên cây hoặc trong Workspace)
2. Nhấn **🧪 Case mới** trên toolbar (hoặc chuột phải → Case mới)
3. Điền form:
   - **Tên case** (bắt buộc)
   - **Mô tả**
   - **Trạng thái**: Bản nháp (Draft) / Sẵn sàng (Ready) / Không hoạt động (Inactive)
   - **URL bắt đầu**: URL dùng khi ghi hình (codegen)
   - **Trình duyệt** & **Ẩn trình duyệt** (kế thừa từ Suite nếu để «Mặc định»)
4. Nhấn **✅ Tạo**

### Sửa entity

1. Chọn Group / Suite / Case trên cây
2. Nhấn **✎ Sửa** trong panel Xem trước (hoặc chuột phải → Sửa)
3. Cập nhật các trường
4. Nhấn **💾 Lưu**

### Đổi trạng thái Suite

- Chuột phải vào Suite → **🚀 Publish** (→ ACTIVE) / **⏸️ Vô hiệu hóa** (→ INACTIVE) / **📝 Đưa về Bản nháp** (→ DRAFT)
- Hoặc: mở Workspace → chọn trạng thái trong combo box **Thông tin Suite**

### Xóa / Sao chép

- Chuột phải → **🗑️ Xóa** — xóa vĩnh viễn (cả thư mục trên đĩa)
- Chuột phải → **📄 Sao chép** — nhân bản Suite hoặc Case

### Vòng đời trạng thái

```mermaid
stateDiagram-v2
    direction LR
    
    state "Suite" as Suite {
        [*] --> DRAFT: Tạo mới
        DRAFT --> ACTIVE: 🚀 Publish
        ACTIVE --> INACTIVE: ⏸️ Vô hiệu hóa
        INACTIVE --> DRAFT: 📝 Về Bản nháp
        INACTIVE --> ACTIVE: 🚀 Publish lại
        DRAFT --> INACTIVE: ⏸️ Vô hiệu hóa
    }
    
    state "Case" as Case {
        [*] --> Draft: Tạo mới
        Draft --> Ready: 🔴 Sau khi ghi hình
        Ready --> Draft: 📝 Về Bản nháp
        Draft --> Inactive: ⏸️ Vô hiệu hóa
        Ready --> Inactive: ⏸️ Vô hiệu hóa
        Inactive --> Draft: 📝 Về Bản nháp
    }
    
    state "Assertion" as Assertion {
        [*] --> ACTIVE: Thêm mới
        ACTIVE --> INACTIVE: Tắt
        ACTIVE --> DRAFT: Về nháp
        DRAFT --> ACTIVE: Kích hoạt
        INACTIVE --> ACTIVE: Kích hoạt lại
    }
```

| Điều kiện chạy | Yêu cầu |
|----------------|---------|
| **Suite** | Phải ở trạng thái ACTIVE |
| **Case** | Phải ở trạng thái Ready (đã ghi hình) |
| **Assertion** | Phải ACTIVE và thuộc case Ready |
| **Chạy Case đơn** | Case Ready + Suite ACTIVE |
| **Chạy Suite** | Suite ACTIVE |

---

## 5. Workspace — Ghi hình & Chạy test

Workspace mở ra khi bạn double-click vào Suite hoặc Case. Đây là nơi làm việc chính.

### Bố cục Workspace

```mermaid
flowchart TB
    subgraph Workspace["Workspace"]
        direction TB
        BC["🧭 Breadcrumb: Khám phá / Smoke / LoginFlow"]
        
        subgraph Body[" "]
            direction LR
            CaseList["📋 Danh sách Case<br/>🧪 LoginOk<br/>🧪 LoginWrong<br/>[+ Case mới]"]
            
            subgraph Editor["Tab Panel"]
                CodeTab["📝 Tab Code<br/>Mã nguồn Java"]
                AssertTab["✅ Tab Asserts<br/>Danh sách assertion"]
            end
        end
        
        URLRow["🔗 URL: https://..."]
        
        subgraph Actions["Thanh công cụ"]
            direction LR
            Record["🔴 Ghi hình lại"]
            Run["▶️ Chạy Suite"]
            Save["💾 Lưu code"]
            Opts["☐ Dừng khi lỗi<br/>☐ Chung trình duyệt"]
        end
        
        Terminal["🖥️ Terminal — log thời gian thực"]
    end
    
    BC --> Body
    Body --> URLRow
    URLRow --> Actions
    Actions --> Terminal
```


### Tab Code

Hiển thị mã nguồn Java của case được chọn. Bạn có thể chỉnh sửa trực tiếp.

- **💾 Lưu code**: Lưu file `.java` vào thư mục Suite. Nếu code có thay đổi chưa lưu, tab hiển thị **📝 Code \*** và nút Lưu đổi thành **💾 Lưu code \***.
- Khi chuyển case/tab, nếu code chưa lưu, ứng dụng hỏi: **Lưu / Bỏ thay đổi / Ở lại**.

### Tab Asserts

Hiển thị danh sách assertion của case đang chọn. Mỗi hàng có nút **Sửa** và **Xóa**.

- Combo box lọc theo nhóm assertion: Tất cả, Nội dung trang, Phần tử, v.v.
- Nút **[+ Thêm Assert]** mở Assertion Builder.

### Ghi hình (Record)

```mermaid
flowchart TD
    A["👆 Chọn Case trong danh sách"] --> B{"Case có script cũ?"}
    B -->|"Có"| C["⚠️ Xác nhận ghi đè"]
    B -->|"Không"| D["🔗 Nhập URL bắt đầu"]
    C --> D
    D --> E{"URL hợp lệ?"}
    E -->|"Không"| F["⚠️ Yêu cầu nhập URL"]
    F --> D
    E -->|"Có"| G["🔴 Mở Playwright Codegen<br/>trong trình duyệt"]
    G --> H["🖱️ Thao tác trên web<br/>(click, gõ, điều hướng)"]
    H --> I["❌ Đóng trình duyệt Codegen"]
    I --> J["💾 Script .java được lưu"]
    J --> K["🏷️ Case → Sẵn sàng (Ready)"]
    K --> L["📋 Assertion Builder tự động mở"]
    
    style G fill:#e74c3c,color:#fff
    style K fill:#27ae60,color:#fff
    style L fill:#3498db,color:#fff
```

> **Lưu ý**: Ghi hình yêu cầu Node.js. Ở chế độ portable, Node.js và Playwright CLI được đóng gói sẵn. Ở chế độ dev, cần `npx playwright` khả dụng.

### Chạy test

#### Luồng chạy test (Run)

```mermaid
flowchart TD
    A["▶️ Người dùng nhấn Run<br/>(Case / Suite / Group / All)"] --> B{"Suite ACTIVE?"}
    B -->|"Không"| C["⏭️ SKIP: Suite chưa ACTIVE"]
    B -->|"Có"| D["📋 Thu thập danh sách Case Ready"]
    D --> E{"Có case nào Ready?"}
    E -->|"Không"| F["⚠️ Không có case để chạy"]
    E -->|"Có"| G{"Chia sẻ trình duyệt?"}
    
    G -->|"Có (>1 case)"| H["🌐 Khởi động SharedBrowserSession"]
    G -->|"Không"| I["🌐 Mỗi case tự mở browser"]
    
    H --> J["🔄 Vòng lặp từng case"]
    I --> J
    
    J --> K{"Case bị SKIP?"}
    K -->|"Có"| L["⏭️ Bỏ qua — có skipReason"]
    K -->|"Không"| M{"Dừng khi lỗi đầu tiên<br/>& đã có case FAILED?"}
    M -->|"Có"| N["⏭️ SKIP: Stopped after first failure"]
    M -->|"Không"| O["📄 Đọc script .java"]
    
    O --> P["🔧 ScriptHarnessTransformer<br/>rewrite cho in-process"]
    P --> Q["💉 Inject soft-assert block<br/>từ AssertCodeGenerator"]
    Q --> R["⚙️ Compile với javax.tools.JavaCompiler"]
    
    R -->|"Lỗi compile"| S["❌ Case → ERROR"]
    R -->|"OK"| T["🏃 Execute harness<br/>qua URLClassLoader"]
    
    T --> U["📊 Đọc assert-results JSON"]
    U --> V{"Có assertion FAILED?"}
    V -->|"Có"| W["❌ Case → FAILED<br/>📸 Chụp ảnh _FAILED.png"]
    V -->|"Không"| X["✅ Case → PASSED<br/>📸 Chụp ảnh _PASSED.png"]
    
    L --> Y{"Còn case?"}
    N --> Y
    S --> Y
    W --> Y
    X --> Y
    
    Y -->|"Có"| J
    Y -->|"Hết"| Z["💾 Lưu RunReport<br/>Hiện Toast kết quả"]
    
    style A fill:#3498db,color:#fff
    style X fill:#27ae60,color:#fff
    style W fill:#e74c3c,color:#fff
    style Z fill:#9b59b6,color:#fff
```

#### Chạy một Case

1. Chọn case trong danh sách (case phải ở trạng thái **Sẵn sàng**)
2. Nhấn nút **▶️** bên cạnh tên case (hoặc chuột phải → Chạy Case này)
3. Xem log trong Terminal

#### Chạy cả Suite

1. Suite phải ở trạng thái **Đang hoạt động** (ACTIVE)
2. Nhấn **▶️ Chạy Suite**
3. Tất cả case Ready trong suite được chạy

#### Tùy chọn chạy

| Tùy chọn | Mặc định | Ý nghĩa |
|----------|----------|---------|
| ☐ Dừng khi lỗi đầu tiên | Tắt | Dừng toàn bộ run khi có case FAILED đầu tiên |
| ☐ Chung trình duyệt | Bật | Chia sẻ một BrowserContext cho tất cả case (cookie/session dùng chung) |

---

## 6. Xây dựng Assertion

Assertion Builder mở ra khi:
- Nhấn **[+ Thêm Assert]** trong tab Asserts của Workspace
- Tự động mở sau khi ghi hình xong

### Giao diện Assertion Builder

```
┌──────────────────────────────────────────────┐
│ 📋 Asserts: LoginOk                          │
├──────────────────────────────────────────────┤
│ Danh sách assertion (kéo thả để sắp xếp)     │
│ ┌──────────────────────────────────────────┐ │
│ │ 🟢 Kiểm tra tiêu đề trang = "Login"      │ │
│ │ 📝 Kiểm tra URL chứa "/dashboard"   [Sửa][Xóa]│
│ └──────────────────────────────────────────┘ │
│                                              │
│ [+ Thêm]                                     │
│                                              │
│ Loại: [Kiểm tra tiêu đề trang ▾]             │
│ Giá trị: [Login                    ]         │
│ Locator: [                        ]          │
│ Trạng thái: [🟢 Đang hoạt động ▾]            │
│ ☐ Khớp chính xác    Timeout: [30]s           │
│                                              │
│                              [Hủy] [💾 Lưu]  │
└──────────────────────────────────────────────┘
```

### 46 loại Assertion — 8 nhóm

```markmap
# 46 Loại Assertion
## Nội dung trang
- Tiêu đề trang
- URL hiện tại
- Văn bản trang
- HTML nguồn
## Phần tử
- Văn bản phần tử
- Tồn tại / Hiển thị / Ẩn
- Enabled / Disabled
- Thuộc tính (attribute)
- CSS class / CSS style
- Số lượng phần tử
- Văn bản chứa (contains)
- Vị trí / Kích thước
- Focus / Placeholder
- Value / Checked / Selected
## Ảnh & Media
- Alt text
- src ảnh
- Kích thước ảnh
- Trạng thái tải
- Video / Audio
## File & Download
- File đã tải xuống
- Tên file
- Kích thước file
- Nội dung file
## Bảng (Table)
- Số dòng / Số cột
- Giá trị ô
- Tiêu đề cột
- Tổng số dòng
## Form & Input
- Giá trị input
- Validation
- Required / Pattern
- Maxlength
- Disabled / Placeholder
- Checked
## Trang & Mạng
- HTTP status
- Response time
- Console error
- JavaScript error
- Cookie
- localStorage
## JS Tùy chỉnh
- Đoạn JavaScript custom
```

### Thêm assertion

1. Chọn **Loại** từ dropdown
2. Điền **Giá trị** mong đợi (và Locator nếu cần)
3. Chọn **Trạng thái**: ACTIVE (chạy) / DRAFT (nháp) / INACTIVE (tắt)
4. Tùy chọn: ☐ Khớp chính xác (exact match), Timeout (ms)
5. Nhấn **+ Thêm** (hoặc chọn hàng → Sửa → nút đổi thành Cập nhật)
6. Nhấn **💾 Lưu** để ghi vào `suite.json`

### Sắp xếp assertion

Kéo thả các hàng trong danh sách để thay đổi thứ tự thực thi.

### Xóa assertion

Nhấn nút **Xóa** trên hàng assertion.

---

## 7. Chạy test & Báo cáo

### Các phạm vi chạy

| Phạm vi | Cách kích hoạt | Mô tả |
|---------|---------------|-------|
| **Tất cả** | Sidebar ▶️ Chạy tất cả | Mọi Suite ACTIVE + Case Ready trong toàn dự án |
| **Nhóm** | Chuột phải vào Nhóm → Chạy nhóm | Đệ quy tất cả Suite trong Nhóm |
| **Suite** | Nút ▶️ Chạy Suite trong Workspace | Tất cả Case Ready trong Suite |
| **Case** | Nút ▶️ trên hàng case | Một case đơn lẻ |

### Trang Báo cáo

Vào **📊 Báo cáo** từ thanh bên để xem lịch sử chạy:

| Cột | Ý nghĩa |
|-----|---------|
| Run ID | Mã lần chạy (yyyyMMdd-HHmmss) |
| Phạm vi | ALL / GROUP / SUITE / CASE |
| Mục tiêu | Tên scope được chạy |
| Kết quả | 🟢 x đạt / 🔴 y thất bại / ⏭️ z bỏ qua |
| Thời gian | Tổng thời gian chạy |
| Trình duyệt | Browser đã dùng |
| Bắt đầu | Thời điểm bắt đầu |

**Bộ lọc**: combo box cho phép lọc theo phạm vi, kết quả (Tất cả / Có lỗi / Toàn đạt), Suite.

### Trang Chi tiết báo cáo

Click vào hàng trong trang Báo cáo để xem chi tiết:

- **Tổng quan**: số case đạt / thất bại / bỏ qua, thời gian, cấu hình
- **Bảng Cases**: từng case với trạng thái, thời gian, lý do lỗi
- **Bảng Asserts**: chọn một case để xem chi tiết từng assertion — loại, giá trị, kết quả, thông báo lỗi
- **Ảnh chụp màn hình**: mỗi case có ảnh `_PASSED.png` hoặc `_FAILED.png`
- **Nút Xuất Excel**: xuất báo cáo ra file `.xlsx`

### Logic bỏ qua (skip)

```mermaid
flowchart TD
    A["📋 Case được thu thập"] --> B{"Suite có ACTIVE?"}
    B -->|"Không"| SKIP1["⏭️ SKIP: Suite status is DRAFT/INACTIVE"]
    B -->|"Có"| C{"Suite có ACTIVE assertion<br/>ở bất kỳ case nào?"}
    C -->|"Không"| SKIP2["⏭️ SKIP: Suite has no active asserts"]
    C -->|"Có"| D{"Case đã Ready?"}
    D -->|"Không"| SKIP3["⏭️ SKIP: No script (case Draft)"]
    D -->|"Có"| E{"Case có ACTIVE assertion?"}
    E -->|"Không"| SKIP4["⏭️ SKIP: No active asserts on this case"]
    E -->|"Có"| F{"StopOnFirstFailure<br/>& đã có FAILED trước đó?"}
    F -->|"Có"| SKIP5["⏭️ SKIP: Stopped after first failure"]
    F -->|"Không"| RUN["▶️ PENDING — Sẽ chạy"]
    
    style RUN fill:#27ae60,color:#fff
    style SKIP1 fill:#f39c12,color:#fff
    style SKIP2 fill:#f39c12,color:#fff
    style SKIP3 fill:#f39c12,color:#fff
    style SKIP4 fill:#f39c12,color:#fff
    style SKIP5 fill:#e67e22,color:#fff
```

---

## 8. Xuất Excel

Từ trang Chi tiết báo cáo, nhấn **📥 Xuất Excel**. File `.xlsx` gồm 3 sheet:

| Sheet | Nội dung |
|-------|----------|
| **Tổng quan** | Run ID, phạm vi, trình duyệt, thời gian, tổng kết Passed/Failed/Skipped |
| **Cases** | Từng case: tên, suite, trạng thái, thời gian, lý do lỗi |
| **Asserts** | Từng assertion: loại, giá trị, kết quả (PASSED/FAILED/SKIPPED), thông báo |

File được lưu vào thư mục `data/exports/` theo mặc định. Có thể chọn vị trí khác qua dialog Save As.

---

## 9. Cấu hình ứng dụng

File cấu hình: `data/config/settings.json`

```json
{
  "browser": "CHROMIUM",
  "headlessDefault": false,
  "shareContextDefault": true,
  "stopOnFirstFailureDefault": false
}
```

| Khóa | Giá trị | Mặc định | Ý nghĩa |
|------|---------|----------|---------|
| `browser` | `CHROMIUM`, `FIREFOX`, `WEBKIT`, `CHROME`, `EDGE` | `CHROMIUM` | Trình duyệt mặc định |
| `headlessDefault` | `true` / `false` | `false` | Chạy ẩn trình duyệt |
| `shareContextDefault` | `true` / `false` | `true` | Chia sẻ BrowserContext giữa các case |
| `stopOnFirstFailureDefault` | `true` / `false` | `false` | Dừng khi gặp lỗi đầu tiên |

### Kế thừa cấu hình

```mermaid
flowchart LR
    subgraph Chain["Chuỗi ưu tiên"]
        direction LR
        C["🎯 Case<br/>browser / headless"] -->|"Để trống / Mặc định"| S["📋 Suite<br/>browser / headless"]
        S -->|"Để trống / Mặc định"| D["⚙️ Settings<br/>browser: CHROMIUM<br/>headlessDefault: false"]
    end
    
    C -->|"Có chọn cụ thể"| R["✅ Dùng luôn"]
    S -->|"Có chọn cụ thể"| R2["✅ Dùng luôn"]
    D --> R3["✅ Dùng mặc định"]
    
    style C fill:#f39c12,color:#fff
    style S fill:#2ecc71,color:#fff
    style D fill:#9b59b6,color:#fff
```

Ví dụ: nếu Case không chọn trình duyệt cụ thể («Mặc định»), hệ thống sẽ kiểm tra Suite. Nếu Suite cũng để mặc định, dùng Playwright Chromium.

### Đường dẫn APP_HOME

Thư mục gốc của ứng dụng (`APP_HOME`) được xác định theo thứ tự:

1. Tham số JVM: `-Dstudio.home=/đường/dẫn`
2. Biến môi trường: `STUDIO_HOME`
3. Biến môi trường: `APP_HOME`
4. Thư mục cha của JAR (chế độ portable)
5. `user.dir` hiện tại (chế độ dev)

---

## 10. Phím tắt & Mẹo

### Phím tắt

| Thao tác | Cách làm |
|----------|----------|
| Mở Workspace | Double-click Suite hoặc Case |
| Bỏ chọn node | Click lại vào node đã chọn |
| Menu ngữ cảnh | Chuột phải (không đổi selection) |
| Thu gọn sidebar | Nút toggle trên thanh bên |

### Mẹo

- **URL tự động điền**: Sau khi ghi hình, URL được trích xuất từ `page.navigate(...)` trong script và tự động điền vào ô URL của case.
- **Code dơ (dirty)**: Tab Code hiển thị dấu `*` khi code có thay đổi chưa lưu. Chuyển case/tab sẽ hỏi xác nhận.
- **Panel Xem trước**: Chọn Suite/Case trên cây để xem metadata mà không cần mở Workspace. Sửa trực tiếp và nhấn Lưu.
- **Terminal**: Tự động hiện khi Record/Run. Có thể ẩn bằng nút **Ẩn** hoặc xóa nội dung bằng **Xóa**.
- **Trợ giúp**: Nhấn nút ❔ trên thanh bên để mở ngăn trợ giúp bên phải.

---

## Cấu trúc thư mục dự án

```
APP_HOME/
├── data/
│   ├── config/settings.json     # Cấu hình người dùng
│   ├── project/                 # Cây Group/Suite/Case
│   │   └── Nhóm/
│   │       ├── group.json
│   │       └── Suite/
│   │           ├── suite.json   # Suite metadata + cases[] + assertions[]
│   │           └── Case.java    # Script Playwright
│   ├── reports/                 # Lịch sử chạy + ảnh chụp
│   └── exports/                 # File Excel xuất ra
└── work/                        # Tạm — an toàn để xóa
    └── harness/                 # Script đã biên dịch cho mỗi lần chạy
```

---

*Playwright Automation Studio — Built with JavaFX 26.0.1 + Playwright 1.61.0*
