package com.disasterrelief.frontend.api;

/** Mirrors backend ResourceItem (GET /api/resources). */
public record ResourceDto(Long id, String name, String category, String unit, boolean perishable) {
}
