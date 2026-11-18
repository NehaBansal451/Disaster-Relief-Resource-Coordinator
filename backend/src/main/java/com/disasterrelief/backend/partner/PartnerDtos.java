package com.disasterrelief.backend.partner;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class PartnerDtos {

    private PartnerDtos() {
    }

    /** organizationId is only needed for an ADMIN; an NGO user's own organization is used automatically. */
    public record OfferRequest(
            Long organizationId,
            @NotNull Long resourceId,
            @NotNull @Min(1) @Max(10_000_000) Integer quantity,
            @Size(max = 1000) String description) {
    }

    public record DecisionRequest(@NotBlank String status) {
    }

    public record ContributionResponse(long id, long organizationId, String organizationName, long resourceId,
                                       String resourceName, int quantity, String description, String status,
                                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
