package com.disasterrelief.backend.system;

/** Response DTO for GET /api/system/status. Contains no secrets and no entity classes. */
public record SystemStatusResponse(String application, String status, Database database) {

    /**
     * @param status           "UP" or "DOWN"
     * @param postgis          PostGIS version text, or null when the database is down
     * @param latestMigration  newest successfully applied Flyway version, or null
     */
    public record Database(String status, String postgis, String latestMigration) {
    }
}
