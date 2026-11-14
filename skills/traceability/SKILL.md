---
name: traceability
description: Workflow to map TEST-ID ↔ SRS/requirements ↔ executable test cases and evidence.
---

# Traceability Skill

Duy trì **chuỗi truy vết** từ requirement → test design → automation/manual execution → evidence. Dùng khi sinh suite mới, review coverage, hoặc audit trước release.

## Identifiers

| Artifact | Convention |
|----------|------------|
| SRS / requirement | `SRS-<module>-<nnn>` hoặc ID từ tool (Jira, Confluence) |
| Test case | `TEST-ID` format team: ví dụ `TC-<feature>-<seq>` |
| Automation | Same `TEST-ID` in `@DisplayName`, test title, or metadata tag |

Một `TEST-ID` map **tối đa một primary SRS**; nhiều SRS có thể share một integration case nếu ghi rõ trong matrix.

## Workflow

### 1. Ingest requirements

- Lấy danh sách SRS cần cover (scope release hoặc delta PR).
- Đánh dấu priority (must / should) và risk (money, PII, auth).

### 2. Derive test cases

- Mỗi SRS must → ít nhất một positive path case.
- Edge/error SRS → negative case với `TEST-ID` riêng.
- Ghi **trace link** trong case header:

```text
TEST-ID: TC-LOGIN-012
SRS: SRS-AUTH-003, SRS-AUTH-007
Type: E2E | API | Manual
```

### 3. Build traceability matrix

| SRS-ID | Requirement summary | TEST-ID | Type | Status | Last run | Evidence |
|--------|---------------------|---------|------|--------|----------|----------|
| SRS-AUTH-003 | Lock after N fails | TC-LOGIN-012 | E2E | Automated | 2026-11-14 | CI #1234 |

Status values: `Planned` | `Automated` | `Manual-only` | `Blocked` | `Deprecated`

### 4. Implementation sync

- Khi thêm/sửa test file: cập nhật `TEST-ID` trong title/annotation; không đổi ID khi đổi locator — tạo ID mới nếu behavior requirement đổi.
- Khi SRS deprecated: mark case `Deprecated` + link replacement SRS.

### 5. Execution evidence

- CI: lưu run URL + artifact path theo `TEST-ID` (report, screenshot).
- Manual: checklist sign-off với tester + date.

### 6. Coverage gate (review)

- **Must SRS**: 100% có ít nhất một `TEST-ID` không-Deprecated.
- Gap list: SRS không có case → tạo case hoặc ghi waiver có approver.
- Orphan tests: `TEST-ID` không map SRS → fix hoặc xóa.

## Agent Checklist

1. Đọc SRS excerpt trước khi sinh script.
2. Mọi case output phải có `TEST-ID` + `SRS` line.
3. Không duplicate `TEST-ID` cho hai behavior khác nhau.
4. Cuối session xuất matrix delta (added/changed/removed rows).

## Vietnamese Summary

Đảm bảo mọi yêu cầu SRS quan trọng đều có test tương ứng, ID thống nhất xuyên suốt spec → case → CI, và có bằng chứng chạy khi audit.