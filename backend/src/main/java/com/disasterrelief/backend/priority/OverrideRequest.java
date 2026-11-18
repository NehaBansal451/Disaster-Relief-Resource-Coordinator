package com.disasterrelief.backend.priority;

import com.disasterrelief.backend.common.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for PATCH /api/requests/{id}/priority-override. A reason is mandatory. */
public record OverrideRequest(@NotNull Severity category, @NotBlank @Size(max = 1000) String reason) {
}
