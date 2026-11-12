package com.disasterrelief.frontend.api;

/** Mirrors backend DisasterResponse. Dates stay ISO-8601 text; formatting is done in the view. */
public record DisasterDto(
        Long id,
        String name,
        String disasterType,
        String severity,
        String status,
        String startedAt,
        String endedAt,
        String description,
        Double latitude,
        Double longitude,
        boolean demo,
        String createdAt) {
}
