package com.disasterrelief.backend.dispatch;

import com.disasterrelief.backend.common.ApiException;

/** Allowed dispatch status changes: DISPATCHED -> IN_TRANSIT -> DELIVERED. Nothing else. */
final class DispatchRules {

    private DispatchRules() {
    }

    static boolean canMove(String from, String to) {
        return ("DISPATCHED".equals(from) && "IN_TRANSIT".equals(to)) || ("IN_TRANSIT".equals(from) && "DELIVERED".equals(to));
    }

    /** Parses the requested status; only IN_TRANSIT and DELIVERED can be requested (DISPATCHED is set at creation). */
    static String requestedStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        if (!s.equals("IN_TRANSIT") && !s.equals("DELIVERED")) {
            throw ApiException.validation("status", "status must be IN_TRANSIT or DELIVERED");
        }
        return s;
    }
}
