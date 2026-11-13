package com.disasterrelief.backend.request;

import com.disasterrelief.backend.common.Severity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * RequestResponse from docs/API_CONTRACT.md. priorityScore/priorityCategory stay null and priorityReasons stays empty
 * until the Day 6 scoring is built. history is filled only on the detail endpoint.
 */
public record RequestResponse(
        long id, long disasterId, String disasterName, long affectedAreaId, String affectedAreaName,
        long resourceId, String resourceName, String unit,
        long requestedBy, String requestedByName,
        int quantity, int affectedPopulation, Severity severity, String description,
        RequestStatus status, String statusReason,
        BigDecimal priorityScore, String priorityCategory, List<String> priorityReasons,
        Long approvedBy, OffsetDateTime approvedAt, OffsetDateTime createdAt,
        List<HistoryEntry> history) {

    public RequestResponse withHistory(List<HistoryEntry> entries) {
        return new RequestResponse(id, disasterId, disasterName, affectedAreaId, affectedAreaName, resourceId,
                resourceName, unit, requestedBy, requestedByName, quantity, affectedPopulation, severity, description,
                status, statusReason, priorityScore, priorityCategory, priorityReasons, approvedBy, approvedAt,
                createdAt, entries);
    }
}
