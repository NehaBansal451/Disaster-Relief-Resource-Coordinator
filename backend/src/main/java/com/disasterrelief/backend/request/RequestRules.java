package com.disasterrelief.backend.request;

import com.disasterrelief.backend.common.ApiException;

/** Business rules that do not need the database, kept separate so they are unit-testable. */
final class RequestRules {

    private RequestRules() {
    }

    /** Trims and checks a mandatory 1-1000 character reason. */
    static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.validation("reason", "A reason is required.");
        }
        if (trimmed.length() > 1000) {
            throw ApiException.validation("reason", "The reason must be at most 1000 characters.");
        }
        return trimmed;
    }

    /** Optional note: blank becomes null. */
    static String optionalNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    /** The single place that says which statuses may be cancelled (contract section 1). */
    static boolean cancellable(RequestStatus status) {
        return status == RequestStatus.PENDING || status == RequestStatus.VERIFIED || status == RequestStatus.APPROVED;
    }

    /** Officers may cancel any cancellable request; the requester only while it is still PENDING. */
    static boolean mayCancel(boolean officer, boolean requester, RequestStatus status) {
        return officer || (requester && status == RequestStatus.PENDING);
    }

    static String trimDescription(String description) {
        return description.trim();
    }
}
