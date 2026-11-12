package com.disasterrelief.frontend.api;

/** Body for POST /api/disasters. status and endedAt are left to the backend defaults (ACTIVE, none). */
public record DisasterForm(
        String name,
        String disasterType,
        String severity,
        String startedAt,
        String description,
        Double latitude,
        Double longitude) {
}
