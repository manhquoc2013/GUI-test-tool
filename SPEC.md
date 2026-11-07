# SPEC.md — UI/UX Redesign & Optimization: Modern Pro IDE (VS Code / Linear Style)

## 1. Objective
Thiết kế lại và tối ưu hóa toàn diện giao diện người dùng (UI/UX) và luồng thao tác quản lý kiểm thử theo phong cách **Modern Pro IDE (VS Code / Linear style)**:
1. **Hệ thống Đa Tab (Multi-Tab Document Bar)**:
   - Cho phép mở cùng lúc nhiều Suite/Case hoặc điều hướng giữa Explorer, Reports, AI Assistant thông qua thanh tab chuyên nghiệp.
   - Mỗi tab có icon phân loại, tên đối tượng, chỉ báo sửa đổi chưa lưu (`*` / dirty dot), và nút đóng tab `✕`.
   - Giữ nguyên trạng thái (code đang soạn, vị trí cuộn, terminal, filter) khi chuyển đổi giữa các tab.
2. **Cây kiểm thử thông minh (Explorer Tree Optimization)**:
   - Thanh tìm kiếm tức thì (instant search as-you-type) theo tên Group, Suite, Case.
   - Các nút lọc trạng thái nhanh dạng chip: `Tất cả`, `Đang hoạt động (ACTIVE)`, `Nháp (DRAFT)`, `Ngừng (INACTIVE)`.
   - Nút thao tác nhanh trực tiếp trên từng dòng cây khi di chuột (hover actions):
     - Group: `[+ Suite]`, `[▶️ Chạy]`, `[✏️ Sửa]`
     - Suite: `[+ Case]`, `[▶️ Chạy]`, `[✏️ Sửa]`, `[📋 Nhân bản]`
     - Case: `[▶️ Chạy]`, `[✏️ Sửa]`, `[📋 Nhân bản]`
3. **Workspace Suite & Case tối ưu (Consolidated Workflow)**:
   - Thanh công cụ hành động tập trung (Unified Action Bar): Gom các nút chính (`🔴 Ghi hình`, `▶️ Chạy test`, `✨ AI Hỗ trợ`, `💾 Lưu code`, `🔗 URL trang test`) vào một hàng gọn gàng, rõ ràng.
   - Bố cục linh hoạt cho Code & Assertions: Hỗ trợ hiển thị song song (Side-by-side Split View) để QA có thể vừa quan sát kịch bản Playwright vừa kiểm tra danh sách Assertions đồng thời mà không phải bấm chuyển tab qua lại liên tục.
4. **Header & Sidebar tinh gọn**:
   - Thanh Breadcrumb động trên Header hiển thị chính xác ngữ cảnh hiện tại (`Dự án > [Nhóm] > [Suite] > [Case]`).
   - Sidebar với dải chỉ báo trạng thái (active border strip) và layout icon + label cân đối, tương phản đạt chuẩn WCAG (>= 4.5:1).
5. **Modern Pro IDE Design System**:
   - Thống nhất bảng màu Catppuccin Mocha / Tokyo Night hiện đại.
   - Bo góc tiêu chuẩn (6px - 8px), border tinh tế (`#2a2b3d`), hiệu ứng hover/focus rõ ràng, typography sắc nét.

## 2. Architecture & Components
```
com.studio/
├── controller/
│   ├── MainShellController.java        # Quản lý Tab Bar, Breadcrumbs, Navigation
│   ├── TabManager.java                 # Quản lý mở, đóng, chuyển đổi đa tab
│   ├── ExplorerPageController.java     # Cây kiểm thử + inline hover actions + search filter
│   ├── SuiteWorkspacePageController.java # Workspace tối ưu action bar + split view Code & Asserts
│   ├── ReportDetailPageController.java # Báo cáo chi tiết & bằng chứng
│   ├── ReportsPageController.java      # Lịch sử báo cáo
│   └── AiAssistantPageController.java  # Trợ lý AI
└── resources/
    ├── fxml/
    │   ├── main_shell.fxml             # Thêm dynamic tab bar & breadcrumb
    │   ├── explorer_page.fxml          # Thêm quick filters & search bar
    │   └── suite_workspace_page.fxml   # Action bar tập trung & split view
    └── css/studio.css                  # Modern Pro IDE styling rules
```

## 3. Verification & Quality Bar
- Kiểm tra tính tương thích ngược với toàn bộ dữ liệu dự án hiện có.
- Đảm bảo tất cả các thao tác CRUD, Ghi hình, Chạy test, AI, Xuất Excel/HTML hoạt động trơn tru.
- Toàn bộ test suite Maven biên dịch và chạy `PASSED` 100%.
