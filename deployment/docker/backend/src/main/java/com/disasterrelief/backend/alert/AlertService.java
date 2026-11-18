package com.disasterrelief.backend.alert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Creates alerts. Types: CRITICAL_REQUEST, LOW_STOCK, OVERCROWDED_SHELTER, REQUEST_APPROVED, DISPATCHED, DELIVERED. */
@Component
public class AlertService {

    private final JdbcClient jdbc;

    public AlertService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public void raise(String type, String severity, String message, String entityType, Long entityId) {
        String text = message.length() > 500 ? message.substring(0, 500) : message;
        jdbc.sql("""
                INSERT INTO alerts (type, severity, message, entity_type, entity_id)
                VALUES (:type, :severity, :message, :entityType, :entityId)
                """)
                .param("type", type).param("severity", severity).param("message", text)
                .param("entityType", entityType).param("entityId", entityId).update();
    }

    /** Low stock rule (documented): a line is low when available stock is 10 percent or less of what is on hand. */
    public static boolean isLowStock(int onHand, int reserved) {
        return onHand > 0 && (onHand - reserved) * 10 <= onHand;
    }
}
