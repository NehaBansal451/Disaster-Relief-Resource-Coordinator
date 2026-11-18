package com.disasterrelief.backend.shelter;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Coordinates;
import com.disasterrelief.backend.common.FieldProblem;
import java.util.ArrayList;
import java.util.List;

final class ShelterRules {

    private ShelterRules() {
    }

    static boolean overcrowded(int capacity, int occupancy) {
        return occupancy > capacity;
    }

    static int freeBeds(int capacity, int occupancy) {
        return Math.max(0, capacity - occupancy);
    }

    /** Status to store: OPEN when the client sent none. */
    static String effectiveStatus(String status) {
        return status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase();
    }

    static boolean isStatus(String status) {
        return status.equals("OPEN") || status.equals("CLOSED") || status.equals("INACTIVE");
    }

    static void validate(ShelterRequest r) {
        List<FieldProblem> errors = new ArrayList<>();
        String status = effectiveStatus(r.status());
        if (!isStatus(status)) {
            errors.add(new FieldProblem("status", "status must be OPEN, CLOSED or INACTIVE"));
        }
        errors.addAll(Coordinates.check(r.latitude(), r.longitude(), true));
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }
}
