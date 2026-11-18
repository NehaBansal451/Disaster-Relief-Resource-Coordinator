package com.disasterrelief.backend.dispatch;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.DispatchResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.TimelineEntry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DispatchRepository {

    static final Map<String, String> SORTS = Map.of("dispatchedAt", "d.dispatched_at", "status", "d.status");

    public record AllocationFacts(String status, long requestId, long resourceId) {
    }

    public record Reservation(long id, long inventoryId, int quantity, long resourceId) {
    }

    public record Balance(int onHand, int reserved) {
    }

    public record Locked(long id, long allocationId, long requestId, long warehouseId, String status) {
    }

    private static final String SELECT = """
            SELECT d.id, d.allocation_id, d.request_id, d.warehouse_id, w.name AS warehouse_name, r.name AS resource_name,
                   r.unit, d.quantity, d.status, d.vehicle, d.driver_name, d.note, d.dispatched_at, d.delivered_at
            FROM dispatches d
            JOIN warehouses w ON w.id = d.warehouse_id
            JOIN need_requests n ON n.id = d.request_id
            JOIN resources r ON r.id = n.resource_id
            """;

    private static final RowMapper<DispatchResponse> MAPPER = (rs, n) -> new DispatchResponse(rs.getLong("id"),
            rs.getLong("allocation_id"), rs.getLong("request_id"), rs.getLong("warehouse_id"),
            rs.getString("warehouse_name"), rs.getString("resource_name"), rs.getString("unit"), rs.getInt("quantity"),
            rs.getString("status"), rs.getString("vehicle"), rs.getString("driver_name"), rs.getString("note"),
            rs.getObject("dispatched_at", OffsetDateTime.class), rs.getObject("delivered_at", OffsetDateTime.class),
            List.of());

    private final JdbcClient jdbc;

    public DispatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public Optional<AllocationFacts> allocationFacts(long allocationId) {
        return jdbc.sql("""
                SELECT a.status, a.request_id, n.resource_id FROM allocations a
                JOIN need_requests n ON n.id = a.request_id WHERE a.id = :id
                """).param("id", allocationId)
                .query((rs, n) -> new AllocationFacts(rs.getString("status"), rs.getLong("request_id"), rs.getLong("resource_id")))
                .optional();
    }

    public boolean warehouseInAllocation(long allocationId, long warehouseId) {
        return jdbc.sql("SELECT count(*) FROM allocation_items WHERE allocation_id = :a AND warehouse_id = :w")
                .param("a", allocationId).param("w", warehouseId).query(Long.class).single() > 0;
    }

    public boolean exists(long allocationId, long warehouseId) {
        return jdbc.sql("SELECT count(*) FROM dispatches WHERE allocation_id = :a AND warehouse_id = :w")
                .param("a", allocationId).param("w", warehouseId).query(Long.class).single() > 0;
    }

    /** Locks the not-yet-dispatched reservations (and their inventory rows) of one warehouse for this allocation. */
    public List<Reservation> lockReservations(long allocationId, long warehouseId) {
        return jdbc.sql("""
                SELECT ar.id, ar.inventory_id, ar.quantity, i.resource_id
                FROM allocation_reservations ar JOIN inventory i ON i.id = ar.inventory_id
                WHERE ar.allocation_id = :a AND i.warehouse_id = :w AND NOT ar.dispatched
                ORDER BY ar.inventory_id FOR UPDATE OF ar, i
                """).param("a", allocationId).param("w", warehouseId)
                .query((rs, n) -> new Reservation(rs.getLong("id"), rs.getLong("inventory_id"), rs.getInt("quantity"),
                        rs.getLong("resource_id")))
                .list();
    }

    /** Stock leaves the warehouse: on hand and reserved both go down by the reserved quantity. */
    public Optional<Balance> deduct(long inventoryId, int quantity) {
        return jdbc.sql("""
                UPDATE inventory SET quantity_on_hand = quantity_on_hand - :q, quantity_reserved = quantity_reserved - :q,
                       updated_at = now()
                WHERE id = :id AND quantity_reserved >= :q AND quantity_on_hand >= :q
                RETURNING quantity_on_hand, quantity_reserved
                """).param("id", inventoryId).param("q", quantity)
                .query((rs, n) -> new Balance(rs.getInt("quantity_on_hand"), rs.getInt("quantity_reserved")))
                .optional();
    }

    public void markDispatched(long reservationId) {
        jdbc.sql("UPDATE allocation_reservations SET dispatched = TRUE WHERE id = :id").param("id", reservationId).update();
    }

    public long insert(long allocationId, long requestId, long warehouseId, int quantity, String vehicle,
                       String driver, String note, long createdBy) {
        return jdbc.sql("""
                INSERT INTO dispatches (allocation_id, request_id, warehouse_id, quantity, vehicle, driver_name, note, created_by)
                VALUES (:a, :r, :w, :q, :v, :d, :n, :by) RETURNING id
                """)
                .param("a", allocationId).param("r", requestId).param("w", warehouseId).param("q", quantity)
                .param("v", vehicle).param("d", driver).param("n", note).param("by", createdBy)
                .query(Long.class).single();
    }

    public void addUpdate(long dispatchId, String status, String note, long actorId) {
        jdbc.sql("INSERT INTO delivery_updates (dispatch_id, status, note, actor_user_id) VALUES (:d, :s, :n, :a)")
                .param("d", dispatchId).param("s", status).param("n", note).param("a", actorId).update();
    }

    public Optional<Locked> lock(long id) {
        return jdbc.sql("SELECT id, allocation_id, request_id, warehouse_id, status FROM dispatches WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query((rs, n) -> new Locked(rs.getLong("id"), rs.getLong("allocation_id"), rs.getLong("request_id"),
                        rs.getLong("warehouse_id"), rs.getString("status")))
                .optional();
    }

    public void setStatus(long id, String status) {
        jdbc.sql("""
                UPDATE dispatches SET status = :s, updated_at = now(),
                       delivered_at = CASE WHEN :s = 'DELIVERED' THEN now() ELSE delivered_at END
                WHERE id = :id
                """).param("id", id).param("s", status).update();
    }

    public int itemCount(long allocationId) {
        return jdbc.sql("SELECT count(*) FROM allocation_items WHERE allocation_id = :a").param("a", allocationId)
                .query(Integer.class).single();
    }

    public int deliveredCount(long allocationId) {
        return jdbc.sql("SELECT count(*) FROM dispatches WHERE allocation_id = :a AND status = 'DELIVERED'")
                .param("a", allocationId).query(Integer.class).single();
    }

    public Optional<DispatchResponse> find(long id) {
        return jdbc.sql(SELECT + " WHERE d.id = :id").param("id", id).query(MAPPER).optional()
                .map(d -> withTimeline(d));
    }

    private DispatchResponse withTimeline(DispatchResponse d) {
        List<TimelineEntry> timeline = jdbc.sql("""
                SELECT u.status, u.note, a.full_name, u.created_at FROM delivery_updates u
                JOIN users a ON a.id = u.actor_user_id WHERE u.dispatch_id = :id ORDER BY u.created_at, u.id
                """).param("id", d.id())
                .query((rs, n) -> new TimelineEntry(rs.getString("status"), rs.getString("note"),
                        rs.getString("full_name"), rs.getObject("created_at", OffsetDateTime.class)))
                .list();
        return new DispatchResponse(d.id(), d.allocationId(), d.requestId(), d.warehouseId(), d.warehouseName(),
                d.resourceName(), d.unit(), d.quantity(), d.status(), d.vehicle(), d.driverName(), d.note(),
                d.dispatchedAt(), d.deliveredAt(), timeline);
    }

    public PageResponse<DispatchResponse> search(String status, Long requestId, Long warehouseId, PageQuery page) {
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR d.status = :status)"
                + " AND (CAST(:req AS bigint) IS NULL OR d.request_id = :req)"
                + " AND (CAST(:wh AS bigint) IS NULL OR d.warehouse_id = :wh)";
        long total = jdbc.sql("SELECT count(*) FROM dispatches d" + where)
                .param("status", status).param("req", requestId).param("wh", warehouseId).query(Long.class).single();
        List<DispatchResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql() + ", d.id DESC LIMIT :limit OFFSET :offset")
                .param("status", status).param("req", requestId).param("wh", warehouseId)
                .param("limit", page.size()).param("offset", page.offset()).query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }
}
