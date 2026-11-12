package com.disasterrelief.frontend.api;

/** What the UI needs to show about the backend. Mirrors backend GET /api/system/status (no secrets). */
public record BackendStatus(boolean reachable, String status, String databaseStatus,
                            String postgis, String latestMigration) {

    /** The backend answered but refused this user (status endpoint is ADMIN only). */
    public static BackendStatus forbidden() {
        return new BackendStatus(true, "FORBIDDEN", "UNKNOWN", null, null);
    }

    public static BackendStatus unreachable() {
        return new BackendStatus(false, "UNREACHABLE", "UNKNOWN", null, null);
    }
}
