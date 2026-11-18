package com.disasterrelief.backend.inventory;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for inventory and its append-only ledger. Values are bound parameters; ORDER BY is whitelisted. */
@Repository
public class InventoryRepository {

    static final Map<String, String> SORTS = Map.of(
            "resource", "lower(r.name)",
            "warehouse", "lower(w.name)",
            "available", "(i.quantity_on_hand - i.quantity_reserved)",
            "expiry", "i.expiry_date");

    private static final String SELECT = """
            SELECT i.id, i.warehouse_id, w.code, w.name AS warehouse_name, i.resource_id, r.name AS resource_name, r.unit,
                   i.batch_number, i.quantity_on_hand, i.quantity_reserved, i.expiry_date
            FROM inventory i
            JOIN warehouses w ON w.id = i.warehouse_id
            JOIN resources r ON r.id = i.resource_id
            """;

    private static final RowMapper<InventoryRow> MAPPER = (rs, n) -> {
        LocalDate expiry = rs.getObject("expiry_date", LocalDate.class);
        int onHand = rs.getInt("quantity_on_hand");
        int reserved = rs.getInt("quantity_reserved");
        return new InventoryRow(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getString("code"),
                rs.getString("warehouse_name"), rs.getLong("resource_id"), rs.getString("resource_name"),
                rs.getString("unit"), rs.getString("batch_number"), onHand, reserved,
                InventoryRules.available(onHand, reserved), expiry,
                InventoryRules.expiryStatus(expiry, InventoryRules.today()));
    };

    private static final RowMapper<LedgerEntry> LEDGER_MAPPER = (rs, n) -> new LedgerEntry(
            rs.getLong("id"), rs.getString("type"), rs.getInt("quantity"), rs.getInt("on_hand_after"),
            rs.getInt("reserved_after"), rs.getString("reference_type"), rs.getObject("reference_id", Long.class),
            rs.getString("note"), rs.getString("full_name"), rs.getObject("created_at", OffsetDateTime.class));

    /** Facts about a warehouse needed to decide whether a receipt is allowed. */
    public record WarehouseInfo(boolean active, Long managerId) {
    }

    /** Result of adding stock: the inventory row and its balances after the change. */
    public record Receipt(long inventoryId, int onHand, int reserved) {
    }

    private final JdbcClient jdbc;

    public InventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public Optional<WarehouseInfo> warehouse(long id) {
        return jdbc.sql("SELECT status, manager_user_id FROM warehouses WHERE id = :id").param("id", id)
                .query((rs, n) -> new WarehouseInfo("ACTIVE".equals(rs.getString("status")),
                        rs.getObject("manager_user_id", Long.class)))
                .optional();
    }

    /** @return Optional of perishable flag when the resource exists and is active */
    public Optional<Boolean> activeResourcePerishable(long id) {
        return jdbc.sql("SELECT perishable FROM resources WHERE id = :id AND active").param("id", id)
                .query(Boolean.class).optional();
    }

    /**
     * Adds stock to a batch, creating the row if needed. Returns empty when the batch already exists with a different
     * expiry date (the caller reports a conflict instead of silently changing the expiry).
     */
    public Optional<Receipt> addStock(long warehouseId, long resourceId, String batch, int quantity, LocalDate expiry) {
        return jdbc.sql("""
                INSERT INTO inventory (warehouse_id, resource_id, batch_number, quantity_on_hand, expiry_date)
                VALUES (:w, :r, :b, :q, :e)
                ON CONFLICT (warehouse_id, resource_id, batch_number) DO UPDATE
                SET quantity_on_hand = inventory.quantity_on_hand + EXCLUDED.quantity_on_hand,
                    expiry_date = COALESCE(EXCLUDED.expiry_date, inventory.expiry_date), updated_at = now()
                WHERE EXCLUDED.expiry_date IS NULL OR inventory.expiry_date IS NOT DISTINCT FROM EXCLUDED.expiry_date
                RETURNING id, quantity_on_hand, quantity_reserved
                """)
                .param("w", warehouseId).param("r", resourceId).param("b", batch).param("q", quantity)
                .param("e", expiry)
                .query((rs, n) -> new Receipt(rs.getLong("id"), rs.getInt("quantity_on_hand"), rs.getInt("quantity_reserved")))
                .optional();
    }

    /**
     * Appends one ledger row. The database CHECK decides which combinations are legal (for example DISPATCH needs
     * a request and equal negative deltas, RECEIPT needs a positive on-hand delta).
     */
    public void addLedger(long inventoryId, String type, int deltaOnHand, int deltaReserved, Long requestId,
                          String reason, String externalReference, long actorId) {
        jdbc.sql("""
                INSERT INTO inventory_transactions (inventory_id, transaction_type, quantity_delta_on_hand,
                    quantity_delta_reserved, request_id, actor_user_id, reason, external_reference)
                VALUES (:inv, :type, :dOn, :dRes, :req, :actor, :reason, :ext)
                """)
                .param("inv", inventoryId).param("type", type).param("dOn", deltaOnHand).param("dRes", deltaReserved)
                .param("req", requestId).param("actor", actorId).param("reason", reason).param("ext", externalReference)
                .update();
    }

    public Optional<InventoryRow> findById(long id) {
        return jdbc.sql(SELECT + " WHERE i.id = :id").param("id", id).query(MAPPER).optional();
    }

    public PageResponse<InventoryRow> search(Long warehouseId, Long resourceId, boolean expiringOnly, PageQuery page) {
        String where = " WHERE (CAST(:w AS bigint) IS NULL OR i.warehouse_id = :w)"
                + " AND (CAST(:r AS bigint) IS NULL OR i.resource_id = :r)"
                + " AND (:expiring = FALSE OR (i.expiry_date IS NOT NULL AND i.expiry_date <= current_date + 30))";
        long total = jdbc.sql("SELECT count(*) FROM inventory i" + where)
                .param("w", warehouseId).param("r", resourceId).param("expiring", expiringOnly)
                .query(Long.class).single();
        List<InventoryRow> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql() + ", i.id"
                        + " LIMIT :limit OFFSET :offset")
                .param("w", warehouseId).param("r", resourceId).param("expiring", expiringOnly)
                .param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }

    public boolean exists(long id) {
        return jdbc.sql("SELECT count(*) FROM inventory WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    /** Balances after each movement are the running totals of the signed deltas, so they cannot drift from the ledger. */
    public List<LedgerEntry> ledger(long inventoryId, int limit) {
        return jdbc.sql("""
                SELECT t.id, t.transaction_type AS type,
                       CASE WHEN t.quantity_delta_on_hand <> 0 THEN t.quantity_delta_on_hand
                            ELSE t.quantity_delta_reserved END AS quantity,
                       sum(t.quantity_delta_on_hand) OVER w AS on_hand_after,
                       sum(t.quantity_delta_reserved) OVER w AS reserved_after,
                       CASE WHEN t.request_id IS NOT NULL THEN 'REQUEST' END AS reference_type,
                       t.request_id AS reference_id, t.reason AS note, u.full_name, t.created_at
                FROM inventory_transactions t JOIN users u ON u.id = t.actor_user_id
                WHERE t.inventory_id = :id
                WINDOW w AS (PARTITION BY t.inventory_id ORDER BY t.id)
                ORDER BY t.created_at DESC, t.id DESC LIMIT :limit
                """).param("id", inventoryId).param("limit", limit).query(LEDGER_MAPPER).list();
    }
}
