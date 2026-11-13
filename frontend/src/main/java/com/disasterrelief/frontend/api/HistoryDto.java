package com.disasterrelief.frontend.api;

/** Mirrors backend HistoryEntry. */
public record HistoryDto(String action, String fromStatus, String toStatus, Long actorId, String actorName,
                         String actorRole, String reason, String createdAt) {
}
