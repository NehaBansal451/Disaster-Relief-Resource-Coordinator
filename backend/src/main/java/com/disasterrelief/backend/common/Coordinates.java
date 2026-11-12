package com.disasterrelief.backend.common;

import java.util.ArrayList;
import java.util.List;

/** WGS 84 coordinate checks shared by disasters and affected areas. */
public final class Coordinates {

    private Coordinates() {
    }

    /**
     * @param required true when a location is mandatory (affected areas); false when both may be absent (disaster epicentre)
     */
    public static List<FieldProblem> check(Double latitude, Double longitude, boolean required) {
        List<FieldProblem> errors = new ArrayList<>();
        if (latitude == null && longitude == null) {
            if (required) {
                errors.add(new FieldProblem("latitude", "latitude is required"));
                errors.add(new FieldProblem("longitude", "longitude is required"));
            }
            return errors;
        }
        if (latitude == null) {
            errors.add(new FieldProblem("latitude", "latitude is required when longitude is given"));
        } else if (latitude.isNaN() || latitude < -90.0 || latitude > 90.0) {
            errors.add(new FieldProblem("latitude", "latitude must be between -90 and 90"));
        }
        if (longitude == null) {
            errors.add(new FieldProblem("longitude", "longitude is required when latitude is given"));
        } else if (longitude.isNaN() || longitude < -180.0 || longitude > 180.0) {
            errors.add(new FieldProblem("longitude", "longitude must be between -180 and 180"));
        }
        return errors;
    }
}
