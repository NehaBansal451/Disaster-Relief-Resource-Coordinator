package com.disasterrelief.backend.disaster;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Coordinates;
import com.disasterrelief.backend.common.FieldProblem;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-field rules that annotations cannot express. They mirror the database CHECK constraints
 * (ck_disasters_dates, ck_disasters_closed_has_end) so a bad request gets a clear 400 instead of a database error.
 */
final class DisasterRules {

    private DisasterRules() {
    }

    /** Status to store: ACTIVE when the client did not send one. */
    static DisasterStatus effectiveStatus(DisasterRequest request) {
        return request.status() == null ? DisasterStatus.ACTIVE : request.status();
    }

    static void validate(DisasterRequest r) {
        List<FieldProblem> errors = new ArrayList<>();
        DisasterStatus status = effectiveStatus(r);

        if (r.endedAt() != null && r.startedAt() != null && r.endedAt().isBefore(r.startedAt())) {
            errors.add(new FieldProblem("endedAt", "endedAt must not be before startedAt"));
        }
        if (status == DisasterStatus.CLOSED && r.endedAt() == null) {
            errors.add(new FieldProblem("endedAt", "endedAt is required when status is CLOSED"));
        }
        if (status != DisasterStatus.CLOSED && r.endedAt() != null) {
            errors.add(new FieldProblem("endedAt", "endedAt can only be set when status is CLOSED"));
        }
        errors.addAll(Coordinates.check(r.latitude(), r.longitude(), false));

        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }
}
