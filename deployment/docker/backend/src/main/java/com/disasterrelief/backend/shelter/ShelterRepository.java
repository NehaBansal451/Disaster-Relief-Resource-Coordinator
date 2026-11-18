package com.disasterrelief.backend.shelter;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for the shelters table (V8). Capacity and occupancy are people counts; occupancy above capacity is allowed. */
@Repository
public class ShelterRepository {

    static final Map<String, String> SORTS = Map.of(
            "name", "lower(s.name)",
            "occupancy", "s.current_occupancy",
            "capacity", "s.capacity_people",
            "freeBeds", "GREATEST(0, s.capacity_people - s.current_occupancy)");

    /** :ref is the shelter to measure distance from; pass null when no distance is wanted. */
    private static final String SELECT = """
            SELECT s.id, s.code, s.name, s.address, s.district, s.state, s.capacity_people, s.current_occupancy,
                   s.facility_notes, s.is_accessible, s.status, s.is_demo, ST_Y(s.location::geometry) AS latitude,
                   ST_X(s.location::geometry) AS longitude,
                   CASE WHEN CAST(:ref AS bigint) IS NULL THEN NULL
                        ELSE ST_Distance(s.location, (SELECT location FROM shelters WHERE id = :ref)) / 1000.0 END AS distance_km
            FROM shelters s
            """;

    private static final RowMapper<ShelterResponse> MAPPER = (rs, n) -> {
        int capacity = rs.getInt("capacity_people");
        int occupancy = rs.getInt("current_occupancy");
        Double km = rs.getObject("distance_km", Double.class);
        return new ShelterResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("address"), rs.getString("district"), rs.getString("state"), capacity, occupancy,
                ShelterRules.freeBeds(capacity, occupancy), ShelterRules.overcrowded(capacity, occupancy),
                rs.getString("facility_notes"), rs.getBoolean("is_accessible"), rs.getString("status"),
                rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                rs.getBoolean("is_demo"), km == null ? null : Math.round(km * 10.0) / 10.0);
    };

    private final JdbcClient jdbc;

    public ShelterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** A shelter needs a unique code; one is generated when the client sends none. */
    static String codeFor(ShelterRequest r) {
        return r.code() == null || r.code().isBlank()
                ? "SH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : r.code().trim().toUpperCase();
    }

    public long insert(ShelterRequest r) {
        return jdbc.sql("""
                INSERT INTO shelters (code, name, address, district, state, capacity_people, current_occupancy,
                                      facility_notes, is_accessible, status, location)
                VALUES (:code, :name, :address, :district, :state, :capacity, :occupancy, :facilities, :accessible,
                        :status, ST_SetSRID(ST_MakePoint(CAST(:lon AS double precision), CAST(:lat AS double precision)), 4326)::geography)
                RETURNING id
                """)
                .param("code", codeFor(r)).param("name", r.name().trim()).param("address", r.address())
                .param("district", r.district()).param("state", r.state()).param("capacity", r.capacity())
                .param("occupancy", r.occupancy() == null ? 0 : r.occupancy()).param("facilities", r.facilities())
                .param("accessible", Boolean.TRUE.equals(r.accessible()))
                .param("status", ShelterRules.effectiveStatus(r.status()))
                .param("lon", r.longitude()).param("lat", r.latitude())
                .query(Long.class).single();
    }

    /** @return true when the shelter exists and was updated */
    public boolean updateOccupancy(long id, int occupancy) {
        return jdbc.sql("UPDATE shelters SET current_occupancy = :o, updated_at = now() WHERE id = :id")
                .param("o", occupancy).param("id", id).update() > 0;
    }

    public Optional<ShelterResponse> findById(long id) {
        return jdbc.sql(SELECT + " WHERE s.id = :id").param("ref", null).param("id", id).query(MAPPER).optional();
    }

    public PageResponse<ShelterResponse> search(String status, boolean overcrowdedOnly, PageQuery page) {
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR s.status = :status)"
                + " AND (:over = FALSE OR s.current_occupancy > s.capacity_people)";
        long total = jdbc.sql("SELECT count(*) FROM shelters s" + where)
                .param("status", status).param("over", overcrowdedOnly)
                .query(Long.class).single();
        List<ShelterResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql() + ", s.id"
                        + " LIMIT :limit OFFSET :offset")
                .param("ref", null).param("status", status).param("over", overcrowdedOnly)
                .param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }

    /** Nearest other OPEN shelters that still have free places, closest first. */
    public List<ShelterResponse> alternatives(long shelterId, int limit) {
        return jdbc.sql(SELECT + " WHERE s.id <> :ref AND s.status = 'OPEN' AND s.current_occupancy < s.capacity_people"
                        + " ORDER BY ST_Distance(s.location, (SELECT location FROM shelters WHERE id = :ref)), s.id LIMIT :limit")
                .param("ref", shelterId).param("limit", limit).query(MAPPER).list();
    }
}
