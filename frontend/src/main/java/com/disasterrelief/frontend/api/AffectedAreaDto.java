package com.disasterrelief.frontend.api;

/** Mirrors backend AffectedAreaResponse. */
public record AffectedAreaDto(
        Long id,
        Long disasterId,
        String name,
        String areaType,
        String district,
        String state,
        Integer populationEstimate,
        String impactLevel,
        String impactDescription,
        Double latitude,
        Double longitude,
        String createdAt) {
}
