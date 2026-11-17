package com.disasterrelief.backend.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Writes one append-only audit row (who, role, what, on which record, when). Called inside the caller's transaction,
 * so an action that rolls back leaves no audit row and an action that commits always has one.
 */
@Component
public class AuditLogger {

    private final JdbcClient jdbc;

    public AuditLogger(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public void log(long actorId, String action, String entityType, Long entityId, String details) {
        jdbc.sql("""
                INSERT INTO audit_logs (actor_user_id, actor_role, action, entity_type, entity_id, details)
                SELECT u.id, u.role, :action, :type, :entityId, :details FROM users u WHERE u.id = :actor
                """)
                .param("actor", actorId).param("action", action).param("type", entityType)
                .param("entityId", entityId).param("details", truncate(details, 1000))
                .update();
    }

    static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
