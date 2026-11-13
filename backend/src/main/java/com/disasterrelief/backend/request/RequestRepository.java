package com.disasterrelief.backend.request;

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

/** SQL for need_requests and request_history. All values are bound parameters; only whitelisted ORDER BY is concatenated. */
@Repository
public class RequestRepository {

    static final Map<String, String> SORTS = Map.of(
            "createdAt", "n.created_at",
            "quantity", "n.quantity",
            "status", "n.status",
            "severity", "CASE n.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END");

    private static final String SELECT = """
            SELECT n.id, n.disaster_id, d.name AS disaster_name, n.affected_area_id, a.name AS area_name,
                   n.resource_id, r.name AS resource_name, r.unit, n.requested_by, u.full_name AS requester_name,
                   n.quantity, n.affected_population, n.severity, n.description, n.status, n.status_reason,
                   n.priority_score, n.priority_category, n.approved_by, n.approved_at, n.created_at
            FROM need_requests n
            JOIN disasters d ON d.id = n.disaster_id
            JOIN affected_areas a ON a.id = n.affected_area_id
            JOIN resources r ON r.id = n.resource_id
            JOIN users u ON u.id = n.requested_by
            """;

    private static final RowMapper<RequestResponse> MAPPER = (rs, i) -> new RequestResponse(
            rs.getLong("id"), rs.getLong("disaster_id"), rs.getString("disaster_name"),
            rs.getLong("affected_area_id"), rs.getString("area_name"),
            rs.getLong("resource_id"), rs.getString("resource_name"), rs.getString("unit"),
            rs.getLong("requested_by"), rs.getString("requester_name"),
            rs.getInt("quantity"), rs.getInt("affected_population"), Severity.valueOf(rs.getString("severity")),
            rs.getString("description"), RequestStatus.valueOf(rs.getString("status")), rs.getString("status_reason"),
            rs.getBigDecimal("priority_score"), rs.getString("priority_category"), List.of(),
            rs.getObject("approved_by", Long.class), rs.getObject("approved_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class), List.of());

    private static final RowMapper<HistoryEntry> HISTORY_MAPPER = (rs, i) -> new HistoryEntry(
            rs.getString("action"),
            rs.getString("from_status") == null ? null : RequestStatus.valueOf(rs.getString("from_status")),
            RequestStatus.valueOf(rs.getString("to_status")),
            rs.getLong("actor_user_id"), rs.getString("full_name"), rs.getString("role"), rs.getString("reason"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public RequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** True when the area exists and belongs to the disaster. */
    public boolean areaBelongsToDisaster(long areaId, long disasterId) {
        return jdbc.sql("SELECT count(*) FROM affected_areas WHERE id = :a AND disaster_id = :d")
                .param("a", areaId).param("d", disasterId).query(Long.class).single() > 0;
    }

    public boolean disasterExists(long id) {
        return jdbc.sql("SELECT count(*) FROM disasters WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    public boolean activeResourceExists(long id) {
        return jdbc.sql("SELECT count(*) FROM resources WHERE id = :id AND active").param("id", id)
                .query(Long.class).single() > 0;
    }

    public long insert(NeedRequestCreate r, long requestedBy) {
        return jdbc.sql("""
                INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity,
                                           affected_population, severity, description)
                VALUES (:disaster, :area, :resource, :user, :quantity, :population, :severity, :description)
                RETURNING id
                """)
                .param("disaster", r.disasterId()).param("area", r.affectedAreaId()).param("resource", r.resourceId())
                .param("user", requestedBy).param("quantity", r.quantity())
                .param("population", r.affectedPopulation() == null ? 0 : r.affectedPopulation())
                .param("severity", r.severity().name()).param("description", RequestRules.trimDescription(r.description()))
                .query(Long.class).single();
    }

    /**
     * Moves a request from one status to another only if it is still in {@code from}. The status check is inside the
     * UPDATE so two officers acting at the same moment cannot both succeed.
     *
     * @return true when a row changed, false when the request is missing or no longer in {@code from}
     */
    public boolean transition(long id, RequestStatus from, RequestStatus to, String reason, Long approvedBy) {
        return jdbc.sql("""
                UPDATE need_requests
                SET status = :to, status_reason = COALESCE(:reason, status_reason), updated_at = now(),
                    approved_by = CASE WHEN CAST(:approvedBy AS bigint) IS NULL THEN approved_by ELSE :approvedBy END,
                    approved_at = CASE WHEN CAST(:approvedBy AS bigint) IS NULL THEN approved_at ELSE now() END
                WHERE id = :id AND status = :from
                """)
                .param("id", id).param("from", from.name()).param("to", to.name())
                .param("reason", reason).param("approvedBy", approvedBy)
                .update() > 0;
    }

    public void addHistory(long requestId, String action, RequestStatus from, RequestStatus to, long actor, String reason) {
        jdbc.sql("""
                INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
                VALUES (:id, :action, :from, :to, :actor, :reason)
                """)
                .param("id", requestId).param("action", action)
                .param("from", from == null ? null : from.name()).param("to", to.name())
                .param("actor", actor).param("reason", reason).update();
    }

    public Optional<RequestResponse> findById(long id) {
        return jdbc.sql(SELECT + " WHERE n.id = :id").param("id", id).query(MAPPER).optional();
    }

    /** Status of a request, used to explain a failed transition. */
    public Optional<RequestStatus> statusOf(long id) {
        return jdbc.sql("SELECT status FROM need_requests WHERE id = :id").param("id", id)
                .query(String.class).optional().map(RequestStatus::valueOf);
    }

    public List<HistoryEntry> history(long requestId) {
        return jdbc.sql("""
                SELECT h.action, h.from_status, h.to_status, h.actor_user_id, u.full_name, u.role, h.reason, h.created_at
                FROM request_history h JOIN users u ON u.id = h.actor_user_id
                WHERE h.request_id = :id ORDER BY h.created_at, h.id
                """).param("id", requestId).query(HISTORY_MAPPER).list();
    }

    public PageResponse<RequestResponse> search(RequestStatus status, Severity severity, Long disasterId, Long areaId,
                                                PageQuery page) {
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR n.status = :status)"
                + " AND (CAST(:severity AS varchar) IS NULL OR n.severity = :severity)"
                + " AND (CAST(:disaster AS bigint) IS NULL OR n.disaster_id = :disaster)"
                + " AND (CAST(:area AS bigint) IS NULL OR n.affected_area_id = :area)";
        String s = status == null ? null : status.name();
        String v = severity == null ? null : severity.name();
        long total = jdbc.sql("SELECT count(*) FROM need_requests n" + where)
                .param("status", s).param("severity", v).param("disaster", disasterId).param("area", areaId)
                .query(Long.class).single();
        List<RequestResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql() + ", n.id DESC"
                        + " LIMIT :limit OFFSET :offset")
                .param("status", s).param("severity", v).param("disaster", disasterId).param("area", areaId)
                .param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }
}
