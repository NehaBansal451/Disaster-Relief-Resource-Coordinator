package com.disasterrelief.backend.affectedarea;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL access to affected_areas (PostGIS geography, so JdbcClient instead of JPA; see DisasterRepository). */
@Repository
public class AffectedAreaRepository {

    static final Map<String, String> SORTS = Map.of(
            "name", "lower(name)",
            "populationEstimate", "population_estimate",
            "createdAt", "created_at",
            "impactLevel", "CASE impact_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END");

    private static final String SELECT = """
            SELECT id, disaster_id, name, area_type, district, state, population_estimate, impact_level,
                   impact_description, ST_Y(location::geometry) AS latitude, ST_X(location::geometry) AS longitude,
                   created_at
            FROM affected_areas
            """;

    private static final RowMapper<AffectedAreaResponse> MAPPER = (rs, rowNum) -> new AffectedAreaResponse(
            rs.getLong("id"),
            rs.getLong("disaster_id"),
            rs.getString("name"),
            AreaType.valueOf(rs.getString("area_type")),
            rs.getString("district"),
            rs.getString("state"),
            rs.getInt("population_estimate"),
            Severity.valueOf(rs.getString("impact_level")),
            rs.getString("impact_description"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public AffectedAreaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** @throws org.springframework.dao.DuplicateKeyException when the disaster already has this name and district */
    public long insert(AffectedAreaRequest r, AreaType type) {
        return jdbc.sql("""
                INSERT INTO affected_areas (disaster_id, name, area_type, district, state, population_estimate,
                                            impact_level, impact_description, location)
                VALUES (:disasterId, :name, :type, :district, :state, :population, :impact, :impactDescription,
                        ST_SetSRID(ST_MakePoint(CAST(:lon AS double precision), CAST(:lat AS double precision)), 4326)::geography)
                RETURNING id
                """)
                .param("disasterId", r.disasterId())
                .param("name", r.name().trim())
                .param("type", type.name())
                .param("district", blankToNull(r.district()))
                .param("state", blankToNull(r.state()))
                .param("population", r.populationEstimate())
                .param("impact", r.impactLevel().name())
                .param("impactDescription", r.impactDescription())
                .param("lon", r.longitude())
                .param("lat", r.latitude())
                .query(Long.class)
                .single();
    }

    public java.util.Optional<AffectedAreaResponse> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    public PageResponse<AffectedAreaResponse> search(Long disasterId, Severity impactLevel, PageQuery page) {
        String where = " WHERE (CAST(:disasterId AS bigint) IS NULL OR disaster_id = :disasterId)"
                + " AND (CAST(:impact AS varchar) IS NULL OR impact_level = :impact)";
        String impact = impactLevel == null ? null : impactLevel.name();

        long total = jdbc.sql("SELECT count(*) FROM affected_areas" + where)
                .param("disasterId", disasterId)
                .param("impact", impact)
                .query(Long.class)
                .single();

        List<AffectedAreaResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql()
                        + " LIMIT :limit OFFSET :offset")
                .param("disasterId", disasterId)
                .param("impact", impact)
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(MAPPER)
                .list();
        return PageResponse.of(rows, page, total);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
