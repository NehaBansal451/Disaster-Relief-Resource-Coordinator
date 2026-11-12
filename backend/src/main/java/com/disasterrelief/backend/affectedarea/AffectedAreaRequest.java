package com.disasterrelief.backend.affectedarea;

import com.disasterrelief.backend.common.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/affected-areas.
 *
 * @param areaType  optional, defaults to VILLAGE
 * @param latitude  centre point of the area (required, checked in {@link AffectedAreaRules})
 * @param longitude centre point of the area (required)
 */
public record AffectedAreaRequest(
        @NotNull Long disasterId,
        @NotBlank @Size(max = 200) String name,
        AreaType areaType,
        @Size(max = 100) String district,
        @Size(max = 100) String state,
        @NotNull @Min(0) @Max(100_000_000) Integer populationEstimate,
        @NotNull Severity impactLevel,
        @Size(max = 5000) String impactDescription,
        Double latitude,
        Double longitude) {
}
