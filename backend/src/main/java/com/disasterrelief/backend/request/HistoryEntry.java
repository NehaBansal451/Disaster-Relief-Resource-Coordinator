package com.disasterrelief.backend.request;

import java.time.OffsetDateTime;

public record HistoryEntry(String action, RequestStatus fromStatus, RequestStatus toStatus,
                           long actorId, String actorName, String actorRole, String reason,
                           OffsetDateTime createdAt) {
}
