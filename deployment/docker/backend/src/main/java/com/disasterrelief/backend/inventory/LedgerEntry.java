package com.disasterrelief.backend.inventory;

import java.time.OffsetDateTime;

public record LedgerEntry(long id, String type, int quantity, int onHandAfter, int reservedAfter,
                          String referenceType, Long referenceId, String note, String actorName,
                          OffsetDateTime createdAt) {
}
