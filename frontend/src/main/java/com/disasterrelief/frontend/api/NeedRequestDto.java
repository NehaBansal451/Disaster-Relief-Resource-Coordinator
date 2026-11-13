package com.disasterrelief.frontend.api;

import java.util.List;

/** Mirrors backend RequestResponse. history is filled only by the detail call. */
public record NeedRequestDto(
        Long id, Long disasterId, String disasterName, Long affectedAreaId, String affectedAreaName,
        Long resourceId, String resourceName, String unit, Long requestedBy, String requestedByName,
        Integer quantity, Integer affectedPopulation, String severity, String description,
        String status, String statusReason, Double priorityScore, String priorityCategory,
        List<String> priorityReasons, Long approvedBy, String approvedAt, String createdAt,
        List<HistoryDto> history) {
}
