package com.disasterrelief.backend.disaster;

import com.disasterrelief.backend.common.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Body for POST /api/disasters and PUT /api/disasters/{id}.
 * Field-level checks are here; cross-field rules (dates, status, coordinates) are in {@link DisasterRules}.
 *
 * @param status    optional on create (defaults to ACTIVE)
 * @param latitude  optional epicentre; give both latitude and longitude or neither
 */
public record DisasterRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull DisasterType disasterType,
        @NotNull Severity severity,
        DisasterStatus status,
        @NotNull OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        @Size(max = 5000) String description,
        Double latitude,
        Double longitude) {
}
