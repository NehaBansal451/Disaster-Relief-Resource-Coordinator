package com.disasterrelief.backend.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disasterrelief.backend.common.ApiException;
import org.junit.jupiter.api.Test;

class RequestRulesTest {

    @Test
    void reasonIsRequiredAndTrimmed() {
        assertEquals("no road", RequestRules.requireReason("  no road "));
        assertThrows(ApiException.class, () -> RequestRules.requireReason(null));
        assertThrows(ApiException.class, () -> RequestRules.requireReason("   "));
        assertThrows(ApiException.class, () -> RequestRules.requireReason("x".repeat(1001)));
    }

    @Test
    void blankNoteBecomesNull() {
        assertNull(RequestRules.optionalNote(" "));
        assertNull(RequestRules.optionalNote(null));
        assertEquals("ok", RequestRules.optionalNote(" ok "));
    }

    @Test
    void onlyThreeStatusesAreCancellable() {
        assertTrue(RequestRules.cancellable(RequestStatus.PENDING));
        assertTrue(RequestRules.cancellable(RequestStatus.VERIFIED));
        assertTrue(RequestRules.cancellable(RequestStatus.APPROVED));
        assertFalse(RequestRules.cancellable(RequestStatus.ALLOCATED));
        assertFalse(RequestRules.cancellable(RequestStatus.REJECTED));
        assertFalse(RequestRules.cancellable(RequestStatus.CANCELLED));
    }

    @Test
    void requesterMayCancelOnlyWhilePending() {
        assertTrue(RequestRules.mayCancel(true, false, RequestStatus.APPROVED));
        assertTrue(RequestRules.mayCancel(false, true, RequestStatus.PENDING));
        assertFalse(RequestRules.mayCancel(false, true, RequestStatus.VERIFIED));
        assertFalse(RequestRules.mayCancel(false, false, RequestStatus.PENDING));
    }
}
