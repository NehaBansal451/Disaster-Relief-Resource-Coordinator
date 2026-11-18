package com.disasterrelief.backend.shelter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body for PATCH /api/shelters/{id}/occupancy: the current number of people. */
public record OccupancyRequest(@NotNull @Min(0) @Max(10_000_000) Integer occupancy) {
}
