---
paths:
  - "docs/RELEASE_NOTES.md"
  - "CHANGELOG.md"
  - ".github/workflows/**/*.yml"
  - "scripts/**/*.sh"
  - "scripts/**/*.bat"
  - "scripts/**/*.py"
---

# Release Management & Release Notes Guidelines

Khi phát hành phiên bản mới (Release & Version Bump) hoặc tạo thông báo phát hành:

1. **Phạm vi nội dung phát hành (Current Version Only)**:
   - Khi công bố bản phát hành mới (GitHub Release body, thông báo chat, release summary cho người dùng), **CHỈ** trình bày nội dung thay đổi / cập nhật của phiên bản vừa được phát hành.
   - **TUYỆT ĐỐI KHÔNG** đính kèm hay lặp lại toàn bộ lịch sử thay đổi của các phiên bản cũ vào nội dung phát hành.

2. **Quản lý lịch sử tập trung (Separation of Concerns)**:
   - Toàn bộ lịch sử tích lũy qua các thời kỳ đã được lưu trữ và quản lý tập trung tại `docs/RELEASE_NOTES.md` và `CHANGELOG.md`.
   - CI/CD workflow (`release-portable.yml`) phải sử dụng công cụ trích xuất (`scripts/extract-release-notes.py`) để chỉ lấy phần ghi chú của phiên bản hiện tại đưa vào phần mô tả của GitHub Release.

