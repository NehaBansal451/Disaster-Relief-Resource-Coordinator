package com.disasterrelief.backend.audit;

import java.time.OffsetDateTime;

public record AuditEntry(long id, long actorId, String actorName, String actorRole, String action, String entityType,
                         Long entityId, String details, OffsetDateTime createdAt) {
}
