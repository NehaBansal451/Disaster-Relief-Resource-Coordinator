package com.disasterrelief.backend.disaster;

import com.disasterrelief.backend.common.Severity;
import java.time.OffsetDateTime;

/** API view of a disaster. {@code demo} is true for fictional seed data so the UI can label it. */
public record DisasterResponse(
        Long id,
        String name,
        DisasterType disasterType,
        Severity severity,
        DisasterStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String description,
        Double latitude,
        Double longitude,
        boolean demo,
        OffsetDateTime createdAt) {
}
