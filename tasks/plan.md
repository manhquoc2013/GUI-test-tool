# Implementation Plan: Modern Pro IDE UI/UX Redesign

## Slices of Delivery

- [ ] **Slice 1: Multi-Tab Document Bar System**
  - Tạo `TabManager.java` quản lý các tab tài liệu (Explorer, Suites, Reports, AI).
  - Cập nhật `main_shell.fxml` và `MainShellController.java` để hiển thị thanh Tab Bar (VS Code / IntelliJ style) với icon, title, close button `✕` và dirty indicator.
  - Tích hợp breadcrumb động (`Dự án > Nhóm > Suite > Case`) trên Header Bar.

- [ ] **Slice 2: Tree Optimization with Inline Hover Actions & Instant Filters**
  - Cập nhật `explorer_page.fxml` bổ sung thanh tìm kiếm nhanh và các chip lọc trạng thái (`Tất cả`, `Hoạt động`, `Nháp`, `Ngừng`).
  - Nâng cấp `ExplorerPageController.java` với `TreeCell` tùy biến có nút thao tác nhanh khi di chuột (`[▶️]`, `[+ Case]`, `[✏️]`, `[📋]`).

- [ ] **Slice 3: Workspace Action Bar & Side-by-Side Code/Asserts View**
  - Thiết kế lại `suite_workspace_page.fxml`:
    - Gom Action Bar tập trung: URL + Nút Mở Browser + Record + Run + AI + Save.
    - Bố cục SplitPane linh hoạt: Code Editor bên trái, Assertions Checklist bên phải (hoặc chuyển đổi Tabbed/Side-by-side).
  - Cập nhật `SuiteWorkspacePageController.java` để điều khiển giao diện mới.

- [ ] **Slice 4: Modern Pro IDE Design Tokens & Visual Polish**
  - Cập nhật `studio.css`:
    - Tab bar styling (active tab highlight, tab hover, close button styling).
    - Tree cell inline action styling (hiện mượt mà khi hover).
    - Unified button groups, input fields, badges, và status indicators.
    - Chuẩn hóa typography, contrast (>= 4.5:1), touch targets (>= 44px).

- [ ] **Slice 5: Verification & Full Suite Run**
  - Chạy toàn bộ test suites Maven (`mvn test`).
  - Cập nhật tài liệu `README.md` & `tasks/learnings.md`.
