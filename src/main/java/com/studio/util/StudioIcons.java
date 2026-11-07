package com.studio.util;

/**
 * Dark-theme icon vocabulary — prefer brightly colored emoji / symbols
 * that stay readable when JavaFX falls back to monochrome (inherits text-fill).
 */
public final class StudioIcons {

    private StudioIcons() {}

    // Node types
    public static final String GROUP = "📁";
    public static final String SUITE = "📋";
    public static final String CASE = "🎯";
    public static final String ASSERT = "☑";
    public static final String PROJECT = "📦";

    // Actions (colored / high-contrast)
    public static final String RUN = "▶";
    public static final String RECORD = "🔴";
    public static final String SAVE = "💾";
    public static final String ADD = "➕";
    public static final String EDIT = "✏";
    public static final String DELETE = "🗑";
    public static final String COPY = "📄";
    public static final String BACK = "◀";
    public static final String CLOSE = "✕";
    public static final String REFRESH = "🔄";
    public static final String OPEN = "📂";
    public static final String EXPORT = "📤";
    public static final String SEARCH = "🔍";
    public static final String FILTER = "🔍";

    // Status — semantic icons (not plain color squares)
    public static final String STATUS_ACTIVE = "●";
    public static final String STATUS_READY = "🚀";
    public static final String STATUS_DRAFT = "📝";
    public static final String STATUS_INACTIVE = "⏸";
    public static final String STATUS_PASSED = "✅";
    public static final String STATUS_FAILED = "❌";
    public static final String STATUS_SKIPPED = "⏭";
    public static final String STATUS_ERROR = "💥";
    public static final String STATUS_PENDING = "⏳";
    public static final String STATUS_RUNNING = "🔄";
}
