package com.disasterrelief.backend.request;

import com.disasterrelief.backend.common.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /api/requests. The area must belong to the disaster (checked in {@link RequestRules}). */
public record NeedRequestCreate(
        @NotNull Long disasterId,
        @NotNull Long affectedAreaId,
        @NotNull Long resourceId,
        @NotNull @Min(1) @Max(10_000_000) Integer quantity,
        @Min(0) @Max(100_000_000) Integer affectedPopulation,
        @NotNull Severity severity,
        @NotBlank @Size(max = 2000) String description) {
}
