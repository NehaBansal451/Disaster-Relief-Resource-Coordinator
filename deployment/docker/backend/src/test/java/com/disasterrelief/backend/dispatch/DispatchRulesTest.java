package com.disasterrelief.backend.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disasterrelief.backend.common.ApiException;
import org.junit.jupiter.api.Test;

class DispatchRulesTest {

    @Test
    void onlyForwardStepsAreAllowed() {
        assertTrue(DispatchRules.canMove("DISPATCHED", "IN_TRANSIT"));
        assertTrue(DispatchRules.canMove("IN_TRANSIT", "DELIVERED"));
        assertFalse(DispatchRules.canMove("DISPATCHED", "DELIVERED"));
        assertFalse(DispatchRules.canMove("DELIVERED", "IN_TRANSIT"));
        assertFalse(DispatchRules.canMove("IN_TRANSIT", "DISPATCHED"));
    }

    @Test
    void requestedStatusIsParsedAndChecked() {
        assertEquals("IN_TRANSIT", DispatchRules.requestedStatus(" in_transit "));
        assertThrows(ApiException.class, () -> DispatchRules.requestedStatus("DISPATCHED"));
        assertThrows(ApiException.class, () -> DispatchRules.requestedStatus(null));
    }
}
