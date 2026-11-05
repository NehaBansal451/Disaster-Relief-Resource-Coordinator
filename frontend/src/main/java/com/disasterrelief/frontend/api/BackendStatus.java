package com.disasterrelief.frontend.api;

/** What the UI needs to show about the backend. Mirrors backend GET /api/system/status (no secrets). */
public record BackendStatus(boolean reachable, String status, String databaseStatus,
                            String postgis, String latestMigration) {

    public static BackendStatus unreachable() {
        return new BackendStatus(false, "UNREACHABLE", "UNKNOWN", null, null);
    }
}
