package com.disasterrelief.backend.alert;

import java.time.OffsetDateTime;

public record AlertItem(long id, String type, String severity, String message, String entityType, Long entityId,
                        String status, OffsetDateTime createdAt, OffsetDateTime readAt) {
}
