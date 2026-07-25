package com.studio.util;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Local timestamps: store ISO-8601 with offset; display HH:mm:ss dd/MM/yyyy. */
public final class EntityTime {

    public static final String DISPLAY_PATTERN = "HH:mm:ss dd/MM/yyyy";
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern(DISPLAY_PATTERN);

    private EntityTime() {}

    /** Current local time as ISO-8601 with offset. */
    public static String nowIso() {
        return OffsetDateTime.now(ZoneId.systemDefault()).toString();
    }

    /** Format stored ISO (or blank) for UI; always local wall clock. */
    public static String formatDisplay(String iso) {
        if (iso == null || iso.isBlank()) {
            return "—";
        }
        try {
            return OffsetDateTime.parse(iso)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DISPLAY);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(iso).toLocalDateTime().format(DISPLAY);
            } catch (DateTimeParseException e2) {
                return iso;
            }
        }
    }

    /** Ensure created/updated exist; returns true if values were filled. */
    public static boolean backfill(String createdAt, String updatedAt,
                                   java.util.function.Consumer<String> setCreated,
                                   java.util.function.Consumer<String> setUpdated) {
        boolean changed = false;
        String now = nowIso();
        if (createdAt == null || createdAt.isBlank()) {
            setCreated.accept(now);
            changed = true;
        }
        if (updatedAt == null || updatedAt.isBlank()) {
            setUpdated.accept(now);
            changed = true;
        }
        return changed;
    }
}
