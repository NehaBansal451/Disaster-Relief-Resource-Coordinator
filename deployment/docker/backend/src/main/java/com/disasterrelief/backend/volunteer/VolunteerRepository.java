package com.disasterrelief.backend.volunteer;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignmentResponse;
import com.disasterrelief.backend.volunteer.VolunteerDtos.CreateRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.VolunteerResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VolunteerRepository {

    static final Map<String, String> SORTS = Map.of("name", "lower(v.full_name)", "createdAt", "v.created_at",
            "district", "lower(v.district)");

    private static final String SELECT = """
            SELECT v.id, v.user_id, v.organization_id, v.full_name, v.phone, v.skills, v.availability, v.district,
                   v.state, ST_Y(v.location::geometry) AS latitude, ST_X(v.location::geometry) AS longitude, v.created_at
            FROM volunteers v
            """;

    private static final RowMapper<VolunteerResponse> MAPPER = (rs, n) -> new VolunteerResponse(rs.getLong("id"),
            rs.getObject("user_id", Long.class), rs.getObject("organization_id", Long.class), rs.getString("full_name"),
            rs.getString("phone"), rs.getString("skills"), rs.getString("availability"), rs.getString("district"),
            rs.getString("state"), rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
            rs.getObject("created_at", OffsetDateTime.class));

    private static final RowMapper<AssignmentResponse> ASSIGNMENT_MAPPER = (rs, n) -> new AssignmentResponse(
            rs.getLong("id"), rs.getLong("volunteer_id"), rs.getLong("disaster_id"),
            rs.getObject("request_id", Long.class), rs.getString("task"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public VolunteerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public long insert(CreateRequest r, long actorId) {
        return jdbc.sql("""
                INSERT INTO volunteers (user_id, organization_id, full_name, phone, skills, district, state, location, created_by)
                VALUES (:user, :org, :name, :phone, :skills, :district, :state,
                        CASE WHEN CAST(:lat AS double precision) IS NULL THEN NULL
                             ELSE ST_SetSRID(ST_MakePoint(CAST(:lon AS double precision), CAST(:lat AS double precision)), 4326)::geography END,
                        :by)
                RETURNING id
                """)
                .param("user", r.userId()).param("org", r.organizationId()).param("name", r.fullName().trim())
                .param("phone", blankToNull(r.phone())).param("skills", blankToNull(r.skills()))
                .param("district", blankToNull(r.district())).param("state", blankToNull(r.state()))
                .param("lat", r.latitude()).param("lon", r.longitude()).param("by", actorId)
                .query(Long.class).single();
    }

    public Optional<VolunteerResponse> find(long id) {
        return jdbc.sql(SELECT + " WHERE v.id = :id").param("id", id).query(MAPPER).optional();
    }

    public Optional<Long> userIdOf(long volunteerId) {
        return jdbc.sql("SELECT user_id FROM volunteers WHERE id = :id").param("id", volunteerId)
                .query(Long.class).optional();
    }

    public boolean userExists(long userId) {
        return jdbc.sql("SELECT count(*) FROM users WHERE id = :id").param("id", userId).query(Long.class).single() > 0;
    }

    public boolean userAlreadyVolunteer(long userId) {
        return jdbc.sql("SELECT count(*) FROM volunteers WHERE user_id = :id").param("id", userId).query(Long.class).single() > 0;
    }

    public boolean organizationExists(long orgId) {
        return jdbc.sql("SELECT count(*) FROM organizations WHERE id = :id").param("id", orgId).query(Long.class).single() > 0;
    }

    public boolean disasterExists(long id) {
        return jdbc.sql("SELECT count(*) FROM disasters WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    public boolean requestExists(long id) {
        return jdbc.sql("SELECT count(*) FROM need_requests WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    public PageResponse<VolunteerResponse> search(String availability, String district, PageQuery page) {
        String where = " WHERE (CAST(:a AS varchar) IS NULL OR v.availability = :a)"
                + " AND (CAST(:d AS varchar) IS NULL OR lower(v.district) = lower(:d))";
        long total = jdbc.sql("SELECT count(*) FROM volunteers v" + where).param("a", availability).param("d", district)
                .query(Long.class).single();
        List<VolunteerResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql()
                        + ", v.id LIMIT :limit OFFSET :offset")
                .param("a", availability).param("d", district).param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }

    public void setAvailability(long id, String availability) {
        jdbc.sql("UPDATE volunteers SET availability = :a, updated_at = now() WHERE id = :id")
                .param("a", availability).param("id", id).update();
    }

    /** Race-safe: only an AVAILABLE volunteer becomes BUSY, so two officers cannot assign the same person at once. */
    public boolean markBusyIfAvailable(long id) {
        return jdbc.sql("UPDATE volunteers SET availability = 'BUSY', updated_at = now() WHERE id = :id AND availability = 'AVAILABLE'")
                .param("id", id).update() > 0;
    }

    public long insertAssignment(long volunteerId, long disasterId, Long requestId, String task, long actorId) {
        return jdbc.sql("""
                INSERT INTO volunteer_assignments (volunteer_id, disaster_id, request_id, task, assigned_by)
                VALUES (:v, :d, :r, :t, :by) RETURNING id
                """)
                .param("v", volunteerId).param("d", disasterId).param("r", requestId).param("t", task.trim())
                .param("by", actorId).query(Long.class).single();
    }

    public Optional<AssignmentResponse> findAssignment(long id) {
        return jdbc.sql("SELECT * FROM volunteer_assignments WHERE id = :id").param("id", id)
                .query(ASSIGNMENT_MAPPER).optional();
    }

    public List<AssignmentResponse> assignmentsOf(long volunteerId) {
        return jdbc.sql("SELECT * FROM volunteer_assignments WHERE volunteer_id = :v ORDER BY id DESC LIMIT 100")
                .param("v", volunteerId).query(ASSIGNMENT_MAPPER).list();
    }

    /** Race-safe move: succeeds only while the assignment is still in the expected status. */
    public boolean moveAssignment(long id, String from, String to) {
        return jdbc.sql("UPDATE volunteer_assignments SET status = :to, updated_at = now() WHERE id = :id AND status = :from")
                .param("to", to).param("from", from).param("id", id).update() > 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
