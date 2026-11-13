package com.studio.util;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/**
 * Assertion type codes (persisted English) + Vietnamese UI labels.
 */
public final class AssertTypeDisplay {

    public static final String[] ALL_TYPES = {
            "TEXT_VISIBLE", "TEXT_NOT_VISIBLE", "TEXT_EXACT", "HEADING_VISIBLE",
            "TITLE_CONTAINS", "TITLE_NOT_CONTAINS", "TITLE_EQUALS",
            "URL_CONTAINS", "URL_NOT_CONTAINS", "URL_EQUALS", "URL_NOT_EQUALS", "URL_MATCHES",
            "PAGE_HAS_TEXT", "PAGE_NOT_CONTAINS_TEXT",
            "ELEMENT_VISIBLE", "ELEMENT_HIDDEN", "ELEMENT_ENABLED",
            "ELEMENT_DISABLED", "ELEMENT_EDITABLE", "ELEMENT_FOCUSED", "ELEMENT_READONLY",
            "ELEMENT_IN_VIEWPORT", "ELEMENT_BBOX_VISIBLE",
            "ELEMENT_CHECKED", "ELEMENT_UNCHECKED",
            "ELEMENT_COUNT", "ELEMENT_HAS_TEXT", "ELEMENT_HAS_VALUE",
            "ATTRIBUTE_EQUALS", "ATTRIBUTE_NOT_EQUALS", "ATTRIBUTE_EXISTS",
            "CSS_CONTAINS", "CSS_PROPERTY_EQUALS", "LINK_HREF_CONTAINS",
            "IMAGE_VISIBLE", "IMAGE_BROKEN", "IMAGE_COUNT",
            "VIDEO_VISIBLE", "AUDIO_VISIBLE", "FILE_DOWNLOADED", "FILE_NAMED", "FILE_EXTENSION",
            "TABLE_HAS_TEXT", "TABLE_ROW_COUNT", "TABLE_CELL_EQUALS", "TABLE_HEADER_CONTAINS",
            "INPUT_VALUE_EQUALS", "INPUT_VALUE_CONTAINS", "PLACEHOLDER_EQUALS",
            "SELECT_HAS_OPTION", "SELECT_SELECTED_TEXT",
            "FORM_VALID", "FORM_INVALID",
            "TOAST_VISIBLE", "MODAL_VISIBLE", "MODAL_HIDDEN", "BREADCRUMB_CONTAINS", "BADGE_COUNT_EQUALS",
            "DIALOG_ALERT_TEXT_CONTAINS", "API_STATUS_EQUALS", "PAGE_LOAD_TIME_MAX",
            "NO_CONSOLE_ERROR", "COOKIE_EXISTS", "COOKIE_VALUE_EQUALS",
            "LOCAL_STORAGE_EXISTS", "LOCAL_STORAGE_EQUALS",
            "SESSION_STORAGE_EXISTS", "SESSION_STORAGE_EQUALS",
            "AI_SEMANTIC_ASSERT", "CUSTOM", "CUSTOM_JS"
    };

    /** Filter keys used in workspace assert list (value stored on combo). */
    public static final String[] FILTER_KEYS = {
            "TEXT", "ELEMENT", "URL", "TITLE", "TABLE", "FORM", "STORAGE", "CUSTOM"
    };

    private AssertTypeDisplay() {}

    public static String normalize(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }

    public static String label(String type) {
        return switch (normalize(type)) {
            case "TEXT_VISIBLE" -> "Văn bản hiển thị";
            case "TEXT_NOT_VISIBLE" -> "Văn bản không hiển thị";
            case "TEXT_EXACT" -> "Văn bản khớp chính xác";
            case "HEADING_VISIBLE" -> "Tiêu đề mục hiển thị";
            case "TITLE_CONTAINS", "PAGE_TITLE_CONTAINS" -> "Tiêu đề trang chứa";
            case "TITLE_NOT_CONTAINS" -> "Tiêu đề trang không chứa";
            case "TITLE_EQUALS", "PAGE_TITLE_EQUALS" -> "Tiêu đề trang bằng";
            case "URL_CONTAINS", "PAGE_URL_CONTAINS" -> "URL chứa";
            case "URL_NOT_CONTAINS" -> "URL không chứa";
            case "URL_EQUALS", "PAGE_URL_EQUALS" -> "URL bằng";
            case "URL_NOT_EQUALS" -> "URL khác";
            case "URL_MATCHES" -> "URL khớp regex";
            case "PAGE_HAS_TEXT", "PAGE_CONTAINS_TEXT" -> "Trang có văn bản";
            case "PAGE_NOT_CONTAINS_TEXT" -> "Trang không có văn bản";
            case "ELEMENT_VISIBLE", "ELEMENT_EXISTS" -> "Phần tử hiển thị";
            case "ELEMENT_HIDDEN", "ELEMENT_NOT_VISIBLE" -> "Phần tử ẩn";
            case "ELEMENT_ENABLED" -> "Phần tử đang bật";
            case "ELEMENT_DISABLED" -> "Phần tử bị tắt";
            case "ELEMENT_EDITABLE" -> "Phần tử sửa được";
            case "ELEMENT_FOCUSED" -> "Phần tử đang focus";
            case "ELEMENT_READONLY" -> "Phần tử chỉ đọc (readonly)";
            case "ELEMENT_IN_VIEWPORT" -> "Phần tử trong vùng nhìn";
            case "ELEMENT_BBOX_VISIBLE" -> "Phần tử có kích thước hiển thị";
            case "ELEMENT_CHECKED", "CHECKBOX_CHECKED" -> "Phần tử đã chọn";
            case "ELEMENT_UNCHECKED" -> "Phần tử chưa chọn";
            case "ELEMENT_COUNT" -> "Số lượng phần tử";
            case "ELEMENT_HAS_TEXT", "ELEMENT_TEXT_CONTAINS" -> "Phần tử có văn bản";
            case "ELEMENT_HAS_VALUE" -> "Phần tử có giá trị";
            case "ATTRIBUTE_EQUALS", "ELEMENT_HAS_ATTRIBUTE" -> "Thuộc tính bằng";
            case "ATTRIBUTE_NOT_EQUALS" -> "Thuộc tính khác";
            case "ATTRIBUTE_EXISTS" -> "Thuộc tính tồn tại";
            case "CSS_CONTAINS" -> "CSS chứa";
            case "CSS_PROPERTY_EQUALS" -> "Thuộc tính CSS bằng";
            case "LINK_HREF_CONTAINS" -> "Link (href) chứa";
            case "IMAGE_VISIBLE" -> "Ảnh hiển thị";
            case "IMAGE_BROKEN" -> "Ảnh không bị lỗi tải";
            case "IMAGE_COUNT" -> "Số lượng ảnh";
            case "VIDEO_VISIBLE" -> "Video hiển thị";
            case "AUDIO_VISIBLE" -> "Audio hiển thị";
            case "FILE_DOWNLOADED" -> "Đã tải file";
            case "FILE_NAMED" -> "Tên file";
            case "FILE_EXTENSION" -> "Phần mở rộng file";
            case "TABLE_HAS_TEXT" -> "Bảng có văn bản";
            case "TABLE_ROW_COUNT" -> "Số dòng bảng";
            case "TABLE_CELL_EQUALS" -> "Ô bảng bằng";
            case "TABLE_HEADER_CONTAINS" -> "Header bảng chứa";
            case "INPUT_VALUE_EQUALS" -> "Giá trị input bằng";
            case "INPUT_VALUE_CONTAINS" -> "Giá trị input chứa";
            case "PLACEHOLDER_EQUALS" -> "Placeholder bằng";
            case "SELECT_HAS_OPTION" -> "Select có lựa chọn";
            case "SELECT_SELECTED_TEXT" -> "Select đang chọn văn bản";
            case "FORM_VALID" -> "Form hợp lệ";
            case "FORM_INVALID" -> "Form không hợp lệ";
            case "TOAST_VISIBLE" -> "Thông báo (Toast) hiển thị";
            case "MODAL_VISIBLE" -> "Hộp thoại (Modal) hiển thị";
            case "MODAL_HIDDEN" -> "Hộp thoại (Modal) đã đóng";
            case "BREADCRUMB_CONTAINS" -> "Breadcrumb chứa";
            case "BADGE_COUNT_EQUALS" -> "Số lượng Badge bằng";
            case "DIALOG_ALERT_TEXT_CONTAINS" -> "Cảnh báo (Alert) chứa";
            case "API_STATUS_EQUALS" -> "Mã trạng thái HTTP/API bằng";
            case "PAGE_LOAD_TIME_MAX" -> "Thời gian tải trang tối đa (ms)";
            case "NO_CONSOLE_ERROR" -> "Không lỗi console";
            case "COOKIE_EXISTS" -> "Cookie tồn tại";
            case "COOKIE_VALUE_EQUALS" -> "Giá trị Cookie bằng";
            case "LOCAL_STORAGE_EXISTS" -> "Local storage tồn tại khóa";
            case "LOCAL_STORAGE_EQUALS" -> "Local storage bằng";
            case "SESSION_STORAGE_EXISTS" -> "Session storage tồn tại khóa";
            case "SESSION_STORAGE_EQUALS" -> "Session storage bằng";
            case "AI_SEMANTIC_ASSERT", "SEMANTIC_ASSERT", "AI_ASSERT" -> "Xác thực ngữ nghĩa AI";
            case "CUSTOM" -> "Tuỳ chỉnh";
            case "CUSTOM_JS", "CUSTOM_JS_TRUE" -> "Tuỳ chỉnh JavaScript";
            default -> type != null && !type.isBlank() ? type : "—";
        };
    }

    public static String filterLabel(String filterKey) {
        if (StatusDisplay.isAll(filterKey)) {
            return StatusDisplay.ALL_LABEL;
        }
        return switch (normalize(filterKey)) {
            case "TEXT" -> "Văn bản";
            case "ELEMENT" -> "Phần tử";
            case "URL" -> "URL";
            case "TITLE" -> "Tiêu đề";
            case "TABLE" -> "Bảng";
            case "FORM" -> "Form";
            case "STORAGE" -> "Lưu trữ";
            case "CUSTOM" -> "Tuỳ chỉnh";
            default -> filterKey != null ? filterKey : "—";
        };
    }

    /** Combo stores English codes; UI shows Vietnamese. */
    public static void wireTypeCombo(ComboBox<String> combo) {
        if (combo == null) return;
        combo.setButtonCell(typeCell());
        combo.setCellFactory(lv -> typeCell());
    }

    public static void wireFilterCombo(ComboBox<String> combo) {
        if (combo == null) return;
        combo.setButtonCell(filterCell());
        combo.setCellFactory(lv -> filterCell());
    }

    private static ListCell<String> typeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : label(item));
            }
        };
    }

    private static ListCell<String> filterCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : filterLabel(item));
            }
        };
    }
}
