package com.disasterrelief.frontend.views.requests;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which buttons to show on a request. UI convenience only: the backend repeats every check, so a wrong guess here can
 * hide a button but never allows an action. Mirrors docs/API_CONTRACT.md section 1.
 */
public final class RequestActions {

    public enum Action { VERIFY, REQUEST_CLARIFICATION, REJECT, APPROVE, CANCEL }

    private RequestActions() {
    }

    public static Set<Action> available(String role, Long userId, String status, Long requesterId) {
        Set<Action> actions = EnumSet.noneOf(Action.class);
        boolean officer = "OFFICER".equals(role);
        boolean owner = userId != null && userId.equals(requesterId);
        if (officer && "PENDING".equals(status)) {
            actions.add(Action.VERIFY);
            actions.add(Action.REQUEST_CLARIFICATION);
            actions.add(Action.REJECT);
        }
        if (officer && "VERIFIED".equals(status)) {
            actions.add(Action.APPROVE);
            actions.add(Action.REJECT);
        }
        boolean cancellable = "PENDING".equals(status) || "VERIFIED".equals(status) || "APPROVED".equals(status);
        if (cancellable && (officer || (owner && "PENDING".equals(status)))) {
            actions.add(Action.CANCEL);
        }
        return actions;
    }

    /** Roles that may submit a need request (assumption O4: citizens cannot yet). */
    public static boolean canSubmit(String role) {
        return "OFFICER".equals(role) || "FIELD_WORKER".equals(role) || "NGO".equals(role);
    }
}
