package com.disasterrelief.backend.request;

/** Matches the need_requests.status CHECK constraint (docs/API_CONTRACT.md section 1). Day 4 sets only the first five. */
public enum RequestStatus {
    PENDING, VERIFIED, APPROVED, ALLOCATED, DISPATCHED, IN_TRANSIT, DELIVERED, REJECTED, CANCELLED
}
