package com.disasterrelief.backend.affectedarea;

import com.disasterrelief.backend.common.Severity;
import java.time.OffsetDateTime;

/** API view of an affected area, including map coordinates (centre point). */
public record AffectedAreaResponse(
        Long id,
        Long disasterId,
        String name,
        AreaType areaType,
        String district,
        String state,
        Integer populationEstimate,
        Severity impactLevel,
        String impactDescription,
        Double latitude,
        Double longitude,
        OffsetDateTime createdAt) {
}
