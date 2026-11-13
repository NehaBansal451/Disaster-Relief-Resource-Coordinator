package com.disasterrelief.frontend.views.requests;

import static com.disasterrelief.frontend.views.requests.RequestActions.Action.APPROVE;
import static com.disasterrelief.frontend.views.requests.RequestActions.Action.CANCEL;
import static com.disasterrelief.frontend.views.requests.RequestActions.Action.REJECT;
import static com.disasterrelief.frontend.views.requests.RequestActions.Action.VERIFY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestActionsTest {

    @Test
    void officerSeesVerifyOnPendingAndApproveOnVerified() {
        assertTrue(RequestActions.available("OFFICER", 1L, "PENDING", 9L).containsAll(Set.of(VERIFY, REJECT, CANCEL)));
        assertEquals(Set.of(APPROVE, REJECT, CANCEL), RequestActions.available("OFFICER", 1L, "VERIFIED", 9L));
        assertEquals(Set.of(CANCEL), RequestActions.available("OFFICER", 1L, "APPROVED", 9L));
        assertTrue(RequestActions.available("OFFICER", 1L, "REJECTED", 9L).isEmpty());
    }

    @Test
    void requesterMayCancelOnlyOwnPendingRequest() {
        assertEquals(Set.of(CANCEL), RequestActions.available("FIELD_WORKER", 9L, "PENDING", 9L));
        assertTrue(RequestActions.available("FIELD_WORKER", 9L, "VERIFIED", 9L).isEmpty());
        assertTrue(RequestActions.available("FIELD_WORKER", 8L, "PENDING", 9L).isEmpty());
    }

    @Test
    void adminHasNoWorkflowActions() {
        assertTrue(RequestActions.available("ADMIN", 1L, "PENDING", 9L).isEmpty());
    }

    @Test
    void onlyThreeRolesMaySubmit() {
        assertTrue(RequestActions.canSubmit("NGO"));
        assertTrue(RequestActions.canSubmit("FIELD_WORKER"));
        assertTrue(RequestActions.canSubmit("OFFICER"));
        assertFalse(RequestActions.canSubmit("CITIZEN"));
        assertFalse(RequestActions.canSubmit("ADMIN"));
    }
}
