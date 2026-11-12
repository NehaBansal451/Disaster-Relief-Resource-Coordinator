package com.disasterrelief.frontend.api;

/** Body for POST /api/affected-areas. */
public record AffectedAreaForm(
        Long disasterId,
        String name,
        String areaType,
        String district,
        String state,
        Integer populationEstimate,
        String impactLevel,
        String impactDescription,
        Double latitude,
        Double longitude) {
}
