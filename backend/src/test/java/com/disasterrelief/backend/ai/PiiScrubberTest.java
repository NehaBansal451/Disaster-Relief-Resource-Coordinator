package com.disasterrelief.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PiiScrubberTest {

    @Test
    void removesPhoneNumbers() {
        String out = PiiScrubber.scrub("Call me at +91 98765 43210 now or 9876543210.");
        assertTrue(out.contains("[PHONE]"), out);
        assertFalse(out.contains("98765"), out);
        assertFalse(out.contains("9876543210"), out);
    }

    @Test
    void removesEmailAddresses() {
        String out = PiiScrubber.scrub("Mail ravi.k@example.com please");
        assertEquals("Mail [EMAIL] please", out);
    }

    @Test
    void removesAadhaarAndPanNumbers() {
        assertEquals("id [ID] and [ID]", PiiScrubber.scrub("id 1234 5678 9012 and ABCDE1234F"));
    }

    @Test
    void keepsOperationalNumbers() {
        String text = "Need 500 blankets and 20 tents for 1200 people in Patiala on 12-11-2026.";
        assertEquals(text, PiiScrubber.scrub(text));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", PiiScrubber.scrub(null));
    }
}
