package com.disasterrelief.backend.shelter;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ShelterRepository {

    static final Map<String, String> SORTS = Map.of(
            "name", "lower(s.name)",
            "occupancy", "s.occupancy",
            "capacity", "s.capacity",
            "freeBeds", "GREATEST(0, s.capacity - s.occupancy)");

    /** :ref is the shelter to measure distance from; pass null when no distance is wanted. */
    private static final String SELECT = """
            SELECT s.id, s.disaster_id, s.name, s.address, s.district, s.state, s.capacity, s.occupancy, s.facilities,
                   s.accessible, s.status, s.is_demo, ST_Y(s.location::geometry) AS latitude,
                   ST_X(s.location::geometry) AS longitude,
                   CASE WHEN CAST(:ref AS bigint) IS NULL THEN NULL
                        ELSE ST_Distance(s.location, (SELECT location FROM shelters WHERE id = :ref)) / 1000.0 END AS distance_km
            FROM shelters s
            """;

    private static final RowMapper<ShelterResponse> MAPPER = (rs, n) -> {
        int capacity = rs.getInt("capacity");
        int occupancy = rs.getInt("occupancy");
        Double km = rs.getObject("distance_km", Double.class);
        return new ShelterResponse(rs.getLong("id"), rs.getObject("disaster_id", Long.class), rs.getString("name"),
                rs.getString("address"), rs.getString("district"), rs.getString("state"), capacity, occupancy,
                ShelterRules.freeBeds(capacity, occupancy), ShelterRules.overcrowded(capacity, occupancy),
                rs.getString("facilities"), rs.getBoolean("accessible"), rs.getString("status"),
                rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                rs.getBoolean("is_demo"), km == null ? null : Math.round(km * 10.0) / 10.0);
    };

    private final JdbcClient jdbc;

    public ShelterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public boolean disasterExists(long id) {
        return jdbc.sql("SELECT count(*) FROM disasters WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    public long insert(ShelterRequest r, long createdBy) {
        return jdbc.sql("""
                INSERT INTO shelters (disaster_id, name, address, district, state, capacity, occupancy, facilities,
                                      accessible, status, location, created_by)
                VALUES (:disaster, :name, :address, :district, :state, :capacity, :occupancy, :facilities, :accessible,
                        :status, ST_SetSRID(ST_MakePoint(CAST(:lon AS double precision), CAST(:lat AS double precision)), 4326)::geography,
                        :createdBy)
                RETURNING id
                """)
                .param("disaster", r.disasterId()).param("name", r.name().trim()).param("address", r.address())
                .param("district", r.district()).param("state", r.state()).param("capacity", r.capacity())
                .param("occupancy", r.occupancy() == null ? 0 : r.occupancy()).param("facilities", r.facilities())
                .param("accessible", Boolean.TRUE.equals(r.accessible()))
                .param("status", ShelterRules.effectiveStatus(r.status()))
                .param("lon", r.longitude()).param("lat", r.latitude()).param("createdBy", createdBy)
                .query(Long.class).single();
    }

    /** @return true when the shelter exists and was updated */
    public boolean updateOccupancy(long id, int occupancy) {
        return jdbc.sql("UPDATE shelters SET occupancy = :o, updated_at = now() WHERE id = :id")
                .param("o", occupancy).param("id", id).update() > 0;
    }

    public Optional<ShelterResponse> findById(long id) {
        return jdbc.sql(SELECT + " WHERE s.id = :id").param("ref", null).param("id", id).query(MAPPER).optional();
    }

    public PageResponse<ShelterResponse> search(String status, Long disasterId, boolean overcrowdedOnly, PageQuery page) {
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR s.status = :status)"
                + " AND (CAST(:disaster AS bigint) IS NULL OR s.disaster_id = :disaster)"
                + " AND (:over = FALSE OR s.occupancy > s.capacity)";
        long total = jdbc.sql("SELECT count(*) FROM shelters s" + where)
                .param("status", status).param("disaster", disasterId).param("over", overcrowdedOnly)
                .query(Long.class).single();
        List<ShelterResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql() + ", s.id"
                        + " LIMIT :limit OFFSET :offset")
                .param("ref", null).param("status", status).param("disaster", disasterId).param("over", overcrowdedOnly)
                .param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }

    /** Nearest other OPEN shelters that still have free beds, closest first. */
    public List<ShelterResponse> alternatives(long shelterId, int limit) {
        return jdbc.sql(SELECT + " WHERE s.id <> :ref AND s.status = 'OPEN' AND s.occupancy < s.capacity"
                        + " ORDER BY ST_Distance(s.location, (SELECT location FROM shelters WHERE id = :ref)), s.id LIMIT :limit")
                .param("ref", shelterId).param("limit", limit).query(MAPPER).list();
    }
}
