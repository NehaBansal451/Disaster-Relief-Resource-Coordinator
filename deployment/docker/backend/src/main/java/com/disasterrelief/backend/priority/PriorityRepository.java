package com.disasterrelief.backend.priority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PriorityRepository {

    /** What scoring needs to know about one request. */
    public record Loaded(String status, String severity, int population, OffsetDateTime createdAt, String category,
                         boolean overridden, boolean hasScore, String reasons) {
    }

    private final JdbcClient jdbc;

    public PriorityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** Locks the request row so two officers cannot calculate and override at the same moment. */
    public Optional<Loaded> loadForUpdate(long id) {
        return jdbc.sql("""
                SELECT n.status, n.severity, n.affected_population, n.created_at, r.category, n.priority_overridden,
                       n.priority_score IS NOT NULL AS has_score, n.priority_reasons
                FROM need_requests n JOIN resources r ON r.id = n.resource_id
                WHERE n.id = :id FOR UPDATE OF n
                """)
                .param("id", id)
                .query((rs, i) -> new Loaded(rs.getString("status"), rs.getString("severity"),
                        rs.getInt("affected_population"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("category"), rs.getBoolean("priority_overridden"), rs.getBoolean("has_score"),
                        rs.getString("priority_reasons")))
                .optional();
    }

    public void store(long id, BigDecimal score, String category, String reasons) {
        jdbc.sql("""
                UPDATE need_requests
                SET priority_score = :score, priority_category = :category, priority_reasons = :reasons,
                    priority_calculated_at = now(), updated_at = now()
                WHERE id = :id AND NOT priority_overridden
                """)
                .param("id", id).param("score", score).param("category", category).param("reasons", reasons).update();
    }

    public void override(long id, String category, String reason, String reasons) {
        jdbc.sql("""
                UPDATE need_requests
                SET priority_category = :category, priority_overridden = TRUE, priority_override_reason = :reason,
                    priority_reasons = :reasons, updated_at = now()
                WHERE id = :id
                """)
                .param("id", id).param("category", category).param("reason", reason).param("reasons", reasons).update();
    }
}
