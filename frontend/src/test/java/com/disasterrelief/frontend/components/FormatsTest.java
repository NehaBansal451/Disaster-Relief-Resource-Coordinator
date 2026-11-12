package com.disasterrelief.frontend.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disasterrelief.frontend.api.BackendException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FormatsTest {

    @Test
    void showsBackendTimestampInIndiaTime() {
        assertEquals("03 Nov 2026, 06:00", Formats.dateTime("2026-11-03T06:00:00+05:30"));
        assertEquals("03 Nov 2026, 11:30", Formats.dateTime("2026-11-03T06:00:00Z"));
    }

    @Test
    void handlesMissingAndUnparseableDates() {
        assertEquals("-", Formats.dateTime(null));
        assertEquals("-", Formats.dateTime(" "));
        assertEquals("not a date", Formats.dateTime("not a date"));
    }

    @Test
    void pickerValueBecomesIsoTextWithIndiaOffset() {
        assertEquals("2026-11-10T08:00+05:30", Formats.toIso(LocalDateTime.of(2026, 11, 10, 8, 0)));
    }

    @Test
    void turnsEnumNamesIntoReadableLabels() {
        assertEquals("Flood", Formats.label("FLOOD"));
        assertEquals("Warehouse manager", Formats.label("WAREHOUSE_MANAGER"));
        assertEquals("-", Formats.label(null));
    }

    @Test
    void formatsNumbersWithGrouping() {
        assertEquals("1,800", Formats.number(1800L));
        assertEquals("-", Formats.number(null));
    }

    @Test
    void feedbackMessagesAreFriendlyAndDoNotLeakDetails() {
        assertEquals("Cannot reach the server. Please try again in a moment.",
                Feedback.message(new BackendException(0, null, "Connection refused: 10.0.0.5:8080", null)));
        assertEquals("Your session has expired. Please log in again.",
                Feedback.message(new BackendException(401, "UNAUTHENTICATED", "x", null)));
        assertEquals("You do not have permission to do this.",
                Feedback.message(new BackendException(403, "FORBIDDEN", "x", null)));
        assertEquals("Disaster 9 is CLOSED.",
                Feedback.message(new BackendException(409, "INVALID_STATE", "Disaster 9 is CLOSED.", null)));
    }
}
