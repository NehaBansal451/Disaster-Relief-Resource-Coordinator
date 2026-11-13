package com.disasterrelief.frontend.api;

/** Body for POST /api/requests. */
public record NeedRequestForm(Long disasterId, Long affectedAreaId, Long resourceId, Integer quantity,
                              Integer affectedPopulation, String severity, String description) {
}
