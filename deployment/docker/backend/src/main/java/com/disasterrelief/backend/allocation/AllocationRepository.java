package com.disasterrelief.backend.allocation;

import com.disasterrelief.backend.allocation.AllocationDtos.ItemResponse;
import com.disasterrelief.backend.allocation.AllocationDtos.NotSelected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for allocations. Row locks (FOR UPDATE) are what stop two officers reserving the same stock. */
@Repository
public class AllocationRepository {

    public record RequestFacts(String status, long resourceId, long areaId, int quantity) {
    }

    public record Row(long id, long requestId, String status, int requestedQty, int allocatedQty, int version,
                      String explanation, String note, Long approvedBy, OffsetDateTime approvedAt) {
    }

    public record Line(long inventoryId, long warehouseId, int onHand, int reserved, LocalDate expiry) {
        public int available() {
            return onHand - reserved;
        }
    }

    public record Balance(int onHand, int reserved) {
    }

    private final JdbcClient jdbc;

    public AllocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public Optional<RequestFacts> requestFacts(long requestId) {
        return jdbc.sql("SELECT status, resource_id, affected_area_id, quantity FROM need_requests WHERE id = :id")
                .param("id", requestId)
                .query((rs, n) -> new RequestFacts(rs.getString("status"), rs.getLong("resource_id"),
                        rs.getLong("affected_area_id"), rs.getInt("quantity")))
                .optional();
    }

    /** Warehouses with unexpired, unreserved stock of the resource, with distance (km) from the affected area. */
    public List<AllocationRules.Candidate> candidates(long resourceId, long areaId) {
        return jdbc.sql("""
                SELECT w.id, w.name, ST_Distance(w.location, a.location) / 1000.0 AS km,
                       sum(i.quantity_on_hand - i.quantity_reserved) AS avail, min(i.expiry_date) AS earliest
                FROM warehouses w
                JOIN inventory i ON i.warehouse_id = w.id AND i.resource_id = :res
                     AND (i.expiry_date IS NULL OR i.expiry_date >= current_date)
                     AND i.quantity_on_hand - i.quantity_reserved > 0
                CROSS JOIN affected_areas a
                WHERE a.id = :area AND w.status = 'ACTIVE'
                GROUP BY w.id, w.name, w.location, a.location
                """)
                .param("res", resourceId).param("area", areaId)
                .query((rs, n) -> new AllocationRules.Candidate(rs.getLong("id"), rs.getString("name"),
                        rs.getInt("avail"), Math.round(rs.getDouble("km") * 10.0) / 10.0,
                        rs.getObject("earliest", LocalDate.class)))
                .list();
    }

    public List<NotSelected> warehousesWithoutStock(List<Long> withStock) {
        List<Long> exclude = withStock.isEmpty() ? List.of(-1L) : withStock;
        return jdbc.sql("SELECT id, name FROM warehouses WHERE status = 'ACTIVE' AND id NOT IN (:ids) ORDER BY id")
                .param("ids", exclude)
                .query((rs, n) -> new NotSelected(rs.getLong("id"), rs.getString("name"),
                        "No unexpired, unreserved stock of this resource."))
                .list();
    }

    /** Demand of other open requests (VERIFIED or APPROVED, not yet allocated) for the same resource. */
    public int competingDemand(long requestId, long resourceId) {
        return jdbc.sql("""
                SELECT coalesce(sum(quantity), 0) FROM need_requests
                WHERE resource_id = :res AND id <> :id AND status IN ('VERIFIED','APPROVED')
                """).param("res", resourceId).param("id", requestId).query(Integer.class).single();
    }

    public boolean hasApprovedAllocation(long requestId) {
        return jdbc.sql("SELECT count(*) FROM allocations WHERE request_id = :id AND status = 'APPROVED'")
                .param("id", requestId).query(Long.class).single() > 0;
    }

    public void markStale(long requestId) {
        jdbc.sql("UPDATE allocations SET status = 'STALE', updated_at = now() WHERE request_id = :id AND status = 'RECOMMENDED'")
                .param("id", requestId).update();
    }

    public long insertAllocation(long requestId, int requested, int allocated, String explanation, long createdBy) {
        return jdbc.sql("""
                INSERT INTO allocations (request_id, requested_qty, allocated_qty, explanation, created_by)
                VALUES (:r, :req, :alloc, :expl, :by) RETURNING id
                """)
                .param("r", requestId).param("req", requested).param("alloc", allocated).param("expl", explanation)
                .param("by", createdBy).query(Long.class).single();
    }

    public void insertItem(long allocationId, long warehouseId, int quantity, Double distanceKm, String reason) {
        jdbc.sql("""
                INSERT INTO allocation_items (allocation_id, warehouse_id, quantity, distance_km, reason)
                VALUES (:a, :w, :q, :d, :reason)
                """)
                .param("a", allocationId).param("w", warehouseId).param("q", quantity).param("d", distanceKm)
                .param("reason", reason).update();
    }

    public void insertNotSelected(long allocationId, long warehouseId, String reason) {
        jdbc.sql("INSERT INTO allocation_not_selected (allocation_id, warehouse_id, reason) VALUES (:a, :w, :r)")
                .param("a", allocationId).param("w", warehouseId).param("r", reason).update();
    }

    public void deleteItems(long allocationId) {
        jdbc.sql("DELETE FROM allocation_items WHERE allocation_id = :a").param("a", allocationId).update();
    }

    public Optional<Row> find(long id) {
        return jdbc.sql(rowSql("") + " WHERE id = :id").param("id", id).query(this::mapRow).optional();
    }

    /** Locks the allocation row for the rest of the transaction. */
    public Optional<Row> lock(long id) {
        return jdbc.sql(rowSql("") + " WHERE id = :id FOR UPDATE").param("id", id).query(this::mapRow).optional();
    }

    public List<Row> forRequest(long requestId) {
        return jdbc.sql(rowSql("") + " WHERE request_id = :r ORDER BY id DESC").param("r", requestId)
                .query(this::mapRow).list();
    }

    private static String rowSql(String unused) {
        return "SELECT id, request_id, status, requested_qty, allocated_qty, version, explanation, note, approved_by, approved_at FROM allocations";
    }

    private Row mapRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getLong("request_id"), rs.getString("status"), rs.getInt("requested_qty"),
                rs.getInt("allocated_qty"), rs.getInt("version"), rs.getString("explanation"), rs.getString("note"),
                rs.getObject("approved_by", Long.class), rs.getObject("approved_at", OffsetDateTime.class));
    }

    public List<ItemResponse> items(long allocationId) {
        return jdbc.sql("""
                SELECT i.warehouse_id, w.name, i.quantity, i.distance_km, i.reason
                FROM allocation_items i JOIN warehouses w ON w.id = i.warehouse_id
                WHERE i.allocation_id = :a ORDER BY i.distance_km NULLS LAST, i.id
                """).param("a", allocationId)
                .query((rs, n) -> new ItemResponse(rs.getLong("warehouse_id"), rs.getString("name"),
                        rs.getInt("quantity"), rs.getObject("distance_km") == null ? null : rs.getBigDecimal("distance_km").doubleValue(),
                        rs.getString("reason")))
                .list();
    }

    public List<NotSelected> notSelected(long allocationId) {
        return jdbc.sql("""
                SELECT s.warehouse_id, w.name, s.reason FROM allocation_not_selected s
                JOIN warehouses w ON w.id = s.warehouse_id WHERE s.allocation_id = :a ORDER BY s.id
                """).param("a", allocationId)
                .query((rs, n) -> new NotSelected(rs.getLong("warehouse_id"), rs.getString("name"), rs.getString("reason")))
                .list();
    }

    public List<Long> activeWarehouseIds(List<Long> ids) {
        return jdbc.sql("SELECT id FROM warehouses WHERE status = 'ACTIVE' AND id IN (:ids)").param("ids", ids)
                .query(Long.class).list();
    }

    /** Locks every unexpired inventory line of the resource in the given warehouses, in id order (no deadlocks). */
    public List<Line> lockLines(long resourceId, List<Long> warehouseIds) {
        return jdbc.sql("""
                SELECT id, warehouse_id, quantity_on_hand, quantity_reserved, expiry_date FROM inventory
                WHERE resource_id = :res AND warehouse_id IN (:ids)
                  AND (expiry_date IS NULL OR expiry_date >= current_date)
                ORDER BY id FOR UPDATE
                """).param("res", resourceId).param("ids", warehouseIds)
                .query((rs, n) -> new Line(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getInt("quantity_on_hand"),
                        rs.getInt("quantity_reserved"), rs.getObject("expiry_date", LocalDate.class)))
                .list();
    }

    /** Adds to reserved only if reserved stays within on-hand. Empty when the guard fails. */
    public Optional<Balance> reserve(long inventoryId, int quantity) {
        return jdbc.sql("""
                UPDATE inventory SET quantity_reserved = quantity_reserved + :q, updated_at = now()
                WHERE id = :id AND quantity_reserved + :q <= quantity_on_hand
                RETURNING quantity_on_hand, quantity_reserved
                """).param("id", inventoryId).param("q", quantity)
                .query((rs, n) -> new Balance(rs.getInt("quantity_on_hand"), rs.getInt("quantity_reserved")))
                .optional();
    }

    public void insertReservation(long allocationId, long inventoryId, int quantity) {
        jdbc.sql("INSERT INTO allocation_reservations (allocation_id, inventory_id, quantity) VALUES (:a, :i, :q)")
                .param("a", allocationId).param("i", inventoryId).param("q", quantity).update();
    }

    public void markApproved(long id, long officerId, String note, int allocatedQty) {
        jdbc.sql("""
                UPDATE allocations SET status = 'APPROVED', allocated_qty = :q, note = :note, approved_by = :by,
                       approved_at = now(), version = version + 1, updated_at = now()
                WHERE id = :id
                """).param("id", id).param("q", allocatedQty).param("note", note).param("by", officerId).update();
    }

    public void markRejected(long id, String note) {
        jdbc.sql("UPDATE allocations SET status = 'REJECTED', note = :note, version = version + 1, updated_at = now() WHERE id = :id")
                .param("id", id).param("note", note).update();
    }
}
