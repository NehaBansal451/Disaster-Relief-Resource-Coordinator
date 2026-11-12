package com.disasterrelief.backend.disaster;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Disasters are read and written with SQL (JdbcClient) rather than JPA entities because the table has a PostGIS
 * geography column and Hibernate Spatial is intentionally not added yet (docs/PROJECT_STATUS.md, Day 3 decision).
 * Every value is bound as a parameter; the only text built into SQL is a whitelisted ORDER BY (see PageQuery).
 */
@Repository
public class DisasterRepository {

    /** API sort names to SQL expressions. Severity is ranked CRITICAL first, not alphabetically. */
    static final Map<String, String> SORTS = Map.of(
            "startedAt", "started_at",
            "createdAt", "created_at",
            "name", "lower(name)",
            "severity", "CASE severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END");

    private static final String SELECT = """
            SELECT id, name, disaster_type, severity, status, started_at, ended_at, description,
                   ST_Y(epicentre::geometry) AS latitude, ST_X(epicentre::geometry) AS longitude,
                   is_demo, created_at
            FROM disasters
            """;

    private static final String POINT = """
            ST_SetSRID(ST_MakePoint(CAST(:lon AS double precision), CAST(:lat AS double precision)), 4326)::geography
            """;

    private static final RowMapper<DisasterResponse> MAPPER = (rs, rowNum) -> new DisasterResponse(
            rs.getLong("id"),
            rs.getString("name"),
            DisasterType.valueOf(rs.getString("disaster_type")),
            Severity.valueOf(rs.getString("severity")),
            DisasterStatus.valueOf(rs.getString("status")),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("ended_at", OffsetDateTime.class),
            rs.getString("description"),
            rs.getObject("latitude", Double.class),
            rs.getObject("longitude", Double.class),
            rs.getBoolean("is_demo"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public DisasterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public long insert(DisasterRequest r, DisasterStatus status, long createdBy) {
        return jdbc.sql("""
                INSERT INTO disasters (name, disaster_type, severity, status, started_at, ended_at, description, epicentre, created_by)
                VALUES (:name, :type, :severity, :status, :startedAt, :endedAt, :description, """ + POINT + """
                , :createdBy)
                RETURNING id
                """)
                .param("name", r.name().trim())
                .param("type", r.disasterType().name())
                .param("severity", r.severity().name())
                .param("status", status.name())
                .param("startedAt", r.startedAt())
                .param("endedAt", r.endedAt())
                .param("description", r.description())
                .param("lon", r.longitude())
                .param("lat", r.latitude())
                .param("createdBy", createdBy)
                .query(Long.class)
                .single();
    }

    /** @return true when a row was updated, false when the id does not exist */
    public boolean update(long id, DisasterRequest r, DisasterStatus status) {
        int rows = jdbc.sql("""
                UPDATE disasters
                SET name = :name, disaster_type = :type, severity = :severity, status = :status,
                    started_at = :startedAt, ended_at = :endedAt, description = :description,
                    epicentre = """ + POINT + """
                    , updated_at = now()
                WHERE id = :id
                """)
                .param("id", id)
                .param("name", r.name().trim())
                .param("type", r.disasterType().name())
                .param("severity", r.severity().name())
                .param("status", status.name())
                .param("startedAt", r.startedAt())
                .param("endedAt", r.endedAt())
                .param("description", r.description())
                .param("lon", r.longitude())
                .param("lat", r.latitude())
                .update();
        return rows > 0;
    }

    public Optional<DisasterResponse> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public PageResponse<DisasterResponse> search(DisasterStatus status, Severity severity, PageQuery page) {
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR status = :status)"
                + " AND (CAST(:severity AS varchar) IS NULL OR severity = :severity)";
        String statusValue = status == null ? null : status.name();
        String severityValue = severity == null ? null : severity.name();

        long total = jdbc.sql("SELECT count(*) FROM disasters" + where)
                .param("status", statusValue)
                .param("severity", severityValue)
                .query(Long.class)
                .single();

        List<DisasterResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql()
                        + " LIMIT :limit OFFSET :offset")
                .param("status", statusValue)
                .param("severity", severityValue)
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(MAPPER)
                .list();
        return PageResponse.of(rows, page, total);
    }
}
