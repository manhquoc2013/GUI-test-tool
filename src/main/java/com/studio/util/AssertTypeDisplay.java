package com.studio.util;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/**
 * Assertion type codes (persisted English) + Vietnamese UI labels.
 */
public final class AssertTypeDisplay {

    public static final String[] ALL_TYPES = {
            "TEXT_VISIBLE", "TEXT_NOT_VISIBLE", "TEXT_EXACT", "HEADING_VISIBLE",
            "TITLE_CONTAINS", "TITLE_EQUALS", "URL_CONTAINS", "URL_EQUALS", "URL_MATCHES",
            "PAGE_HAS_TEXT", "ELEMENT_VISIBLE", "ELEMENT_HIDDEN", "ELEMENT_ENABLED",
            "ELEMENT_DISABLED", "ELEMENT_EDITABLE", "ELEMENT_CHECKED", "ELEMENT_UNCHECKED",
            "ELEMENT_COUNT", "ELEMENT_HAS_TEXT", "ELEMENT_HAS_VALUE", "ATTRIBUTE_EQUALS",
            "CSS_CONTAINS", "LINK_HREF_CONTAINS", "IMAGE_VISIBLE", "IMAGE_COUNT",
            "VIDEO_VISIBLE", "AUDIO_VISIBLE", "FILE_DOWNLOADED", "FILE_NAMED", "FILE_EXTENSION",
            "TABLE_HAS_TEXT", "TABLE_ROW_COUNT", "TABLE_CELL_EQUALS", "TABLE_HEADER_CONTAINS",
            "INPUT_VALUE_EQUALS", "INPUT_VALUE_CONTAINS", "PLACEHOLDER_EQUALS", "SELECT_HAS_OPTION",
            "FORM_VALID", "FORM_INVALID", "NO_CONSOLE_ERROR", "COOKIE_EXISTS",
            "LOCAL_STORAGE_EQUALS", "SESSION_STORAGE_EQUALS", "CUSTOM", "CUSTOM_JS"
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
            case "TITLE_CONTAINS" -> "Tiêu đề trang chứa";
            case "TITLE_EQUALS" -> "Tiêu đề trang bằng";
            case "URL_CONTAINS" -> "URL chứa";
            case "URL_EQUALS" -> "URL bằng";
            case "URL_MATCHES" -> "URL khớp regex";
            case "PAGE_HAS_TEXT" -> "Trang có văn bản";
            case "ELEMENT_VISIBLE" -> "Phần tử hiển thị";
            case "ELEMENT_HIDDEN" -> "Phần tử ẩn";
            case "ELEMENT_ENABLED" -> "Phần tử đang bật";
            case "ELEMENT_DISABLED" -> "Phần tử bị tắt";
            case "ELEMENT_EDITABLE" -> "Phần tử sửa được";
            case "ELEMENT_CHECKED" -> "Phần tử đã chọn";
            case "ELEMENT_UNCHECKED" -> "Phần tử chưa chọn";
            case "ELEMENT_COUNT" -> "Số lượng phần tử";
            case "ELEMENT_HAS_TEXT" -> "Phần tử có văn bản";
            case "ELEMENT_HAS_VALUE" -> "Phần tử có giá trị";
            case "ATTRIBUTE_EQUALS" -> "Thuộc tính bằng";
            case "CSS_CONTAINS" -> "CSS chứa";
            case "LINK_HREF_CONTAINS" -> "Link (href) chứa";
            case "IMAGE_VISIBLE" -> "Ảnh hiển thị";
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
            case "FORM_VALID" -> "Form hợp lệ";
            case "FORM_INVALID" -> "Form không hợp lệ";
            case "NO_CONSOLE_ERROR" -> "Không lỗi console";
            case "COOKIE_EXISTS" -> "Cookie tồn tại";
            case "LOCAL_STORAGE_EQUALS" -> "Local storage bằng";
            case "SESSION_STORAGE_EQUALS" -> "Session storage bằng";
            case "CUSTOM" -> "Tuỳ chỉnh";
            case "CUSTOM_JS" -> "Tuỳ chỉnh JavaScript";
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
