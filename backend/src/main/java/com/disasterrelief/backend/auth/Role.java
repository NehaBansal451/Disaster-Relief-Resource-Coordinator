package com.disasterrelief.backend.auth;

/** User roles. Matches the users.role CHECK constraint after migration V2 (FIELD_WORKER added there). */
public enum Role {
    ADMIN, OFFICER, WAREHOUSE_MANAGER, FIELD_WORKER, NGO, VOLUNTEER, CITIZEN
}
