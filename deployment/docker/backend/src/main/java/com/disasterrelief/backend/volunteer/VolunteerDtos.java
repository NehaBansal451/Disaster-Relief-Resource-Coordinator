package com.disasterrelief.backend.volunteer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class VolunteerDtos {

    private VolunteerDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) String phone,
            @Size(max = 500) String skills,
            @Size(max = 100) String district,
            @Size(max = 100) String state,
            Long userId,
            Long organizationId,
            Double latitude,
            Double longitude) {
    }

    public record AvailabilityRequest(@NotBlank String availability) {
    }

    public record AssignRequest(
            @NotNull Long disasterId,
            Long requestId,
            @NotBlank @Size(max = 500) String task) {
    }

    public record AssignmentStatusRequest(@NotBlank String status) {
    }

    public record VolunteerResponse(long id, Long userId, Long organizationId, String fullName, String phone,
                                    String skills, String availability, String district, String state,
                                    Double latitude, Double longitude, OffsetDateTime createdAt) {
    }

    public record AssignmentResponse(long id, long volunteerId, long disasterId, Long requestId, String task,
                                     String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
