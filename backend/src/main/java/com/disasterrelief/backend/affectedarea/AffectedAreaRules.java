package com.disasterrelief.backend.affectedarea;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Coordinates;
import com.disasterrelief.backend.common.FieldProblem;
import java.util.List;

/** Rules that annotations cannot express. The centre point is mandatory (affected_areas.location is NOT NULL). */
final class AffectedAreaRules {

    private AffectedAreaRules() {
    }

    static AreaType effectiveType(AffectedAreaRequest request) {
        return request.areaType() == null ? AreaType.VILLAGE : request.areaType();
    }

    static void validate(AffectedAreaRequest request) {
        List<FieldProblem> errors = Coordinates.check(request.latitude(), request.longitude(), true);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }
}
