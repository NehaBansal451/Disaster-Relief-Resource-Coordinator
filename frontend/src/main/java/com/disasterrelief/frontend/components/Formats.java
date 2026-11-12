package com.disasterrelief.frontend.components;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Small display helpers shared by the views. */
public final class Formats {

    /** The deployment is for India; all times are shown and entered in this zone. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private Formats() {
    }

    /** ISO-8601 text from the backend to "03 Nov 2026, 06:00"; "-" when absent; the raw text if it cannot be parsed. */
    public static String dateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "-";
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZONE).format(DISPLAY);
        } catch (DateTimeParseException ex) {
            return iso;
        }
    }

    /** Date picker value (no zone) to the ISO text with offset that the backend expects. */
    public static String toIso(LocalDateTime local) {
        return local.atZone(ZONE).toOffsetDateTime().toString();
    }

    /** FLOOD to "Flood", HEATWAVE to "Heatwave", WAREHOUSE_MANAGER to "Warehouse manager". */
    public static String label(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return "-";
        }
        String text = enumName.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String number(Long value) {
        return value == null ? "-" : String.format(Locale.ENGLISH, "%,d", value);
    }
}
