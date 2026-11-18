package com.disasterrelief.backend.analytics;

import com.disasterrelief.backend.analytics.AnalyticsDtos.ShortageRow;
import com.disasterrelief.backend.analytics.AnalyticsDtos.Summary;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only numbers computed from the real tables; nothing here is estimated or stored. */
@Service
public class AnalyticsService {

    private static final String SCOPE = " (CAST(:d AS bigint) IS NULL OR n.disaster_id = :d) ";

    private final JdbcClient jdbc;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional(readOnly = true)
    public Summary summary(Long disasterId) {
        Map<String, Long> byStatus = counts("status", disasterId);
        Map<String, Long> bySeverity = counts("severity", disasterId);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long open = jdbc.sql("SELECT count(*) FROM need_requests n WHERE n.severity = 'CRITICAL' AND n.status IN "
                        + "('PENDING','VERIFIED','APPROVED','ALLOCATED','DISPATCHED','IN_TRANSIT') AND" + SCOPE)
                .param("d", disasterId).query(Long.class).single();
        long[] qty = jdbc.sql("""
                SELECT coalesce(sum(n.quantity) FILTER (WHERE n.status = 'DELIVERED'), 0) AS delivered,
                       coalesce(sum(n.quantity) FILTER (WHERE n.status NOT IN ('REJECTED','CANCELLED')), 0) AS requested
                FROM need_requests n WHERE""" + SCOPE)
                .param("d", disasterId).query((rs, i) -> new long[] {rs.getLong("delivered"), rs.getLong("requested")}).single();
        BigDecimal hours = jdbc.sql("""
                SELECT avg(extract(epoch FROM (d.delivered_at - n.created_at)) / 3600.0)::numeric(12,2)
                FROM dispatches d JOIN need_requests n ON n.id = d.request_id
                WHERE d.status = 'DELIVERED' AND""" + SCOPE)
                .param("d", disasterId).query((rs, i) -> rs.getBigDecimal(1)).optional().orElse(null);
        return new Summary(disasterId, total, byStatus, bySeverity, byStatus.getOrDefault("PENDING", 0L), open,
                qty[0], qty[1], AnalyticsRules.fulfilmentPercent(qty[0], qty[1]),
                hours == null ? null : hours.doubleValue());
    }

    @Transactional(readOnly = true)
    public List<ShortageRow> shortages(Long disasterId) {
        return jdbc.sql("""
                WITH demand AS (
                    SELECT n.resource_id, sum(n.quantity) AS qty, count(*) AS requests
                    FROM need_requests n WHERE n.status IN ('VERIFIED','APPROVED') AND""" + SCOPE + """
                    GROUP BY n.resource_id),
                stock AS (
                    SELECT i.resource_id, sum(i.quantity_on_hand - i.quantity_reserved) AS avail
                    FROM inventory i JOIN warehouses w ON w.id = i.warehouse_id AND w.status = 'ACTIVE'
                    WHERE i.expiry_date IS NULL OR i.expiry_date >= current_date
                    GROUP BY i.resource_id),
                offers AS (
                    SELECT resource_id, sum(quantity) AS qty FROM partner_contributions
                    WHERE status IN ('OFFERED','ACCEPTED') GROUP BY resource_id)
                SELECT r.id, r.name, r.unit, d.requests, d.qty AS demand, coalesce(s.avail, 0) AS available,
                       coalesce(o.qty, 0) AS offered
                FROM demand d JOIN resources r ON r.id = d.resource_id
                     LEFT JOIN stock s ON s.resource_id = d.resource_id
                     LEFT JOIN offers o ON o.resource_id = d.resource_id
                ORDER BY greatest(d.qty - coalesce(s.avail, 0), 0) DESC, r.name
                """)
                .param("d", disasterId)
                .query((rs, i) -> {
                    long demand = rs.getLong("demand");
                    long available = rs.getLong("available");
                    return new ShortageRow(rs.getLong("id"), rs.getString("name"), rs.getString("unit"),
                            rs.getLong("requests"), demand, available, AnalyticsRules.shortage(demand, available),
                            AnalyticsRules.coveragePercent(demand, available), rs.getLong("offered"));
                }).list();
    }

    private Map<String, Long> counts(String column, Long disasterId) {
        Map<String, Long> map = new LinkedHashMap<>();
        jdbc.sql("SELECT n." + column + " AS k, count(*) AS c FROM need_requests n WHERE" + SCOPE
                        + "GROUP BY n." + column + " ORDER BY n." + column)
                .param("d", disasterId).query((rs, i) -> {
                    map.put(rs.getString("k"), rs.getLong("c"));
                    return 0;
                }).list();
        return map;
    }
}
