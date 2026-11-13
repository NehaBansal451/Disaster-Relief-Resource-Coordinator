package com.disasterrelief.backend.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for PATCH /api/requests/{id}/verify. The reason is required unless the decision is VERIFY. */
public record VerifyRequest(@NotNull VerifyDecision decision, @Size(max = 1000) String reason) {
}
