package com.disasterrelief.backend.shelter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /api/shelters. Coordinates are required (the shelter must be placeable on the map). */
public record ShelterRequest(
        @Size(max = 30) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String address,
        @Size(max = 100) String district,
        @Size(max = 100) String state,
        @NotNull @Min(1) @Max(1_000_000) Integer capacity,
        @Min(0) @Max(10_000_000) Integer occupancy,
        @Size(max = 1000) String facilities,
        Boolean accessible,
        String status,
        @NotNull Double latitude,
        @NotNull Double longitude) {
}
