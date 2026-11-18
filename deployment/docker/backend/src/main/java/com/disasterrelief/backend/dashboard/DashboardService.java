package com.disasterrelief.backend.dashboard;

import com.disasterrelief.backend.common.Severity;
import com.disasterrelief.backend.request.RequestStatus;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final JdbcClient jdbc;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        long activeDisasters = one("SELECT count(*) FROM disasters WHERE status = 'ACTIVE'");
        long population = one("""
                SELECT coalesce(sum(a.population_estimate), 0)
                FROM affected_areas a JOIN disasters d ON d.id = a.disaster_id WHERE d.status = 'ACTIVE'
                """);
        Map<String, Long> byStatus = zeroFilled(RequestStatus.values());
        fill("SELECT status AS k, count(*) AS c FROM need_requests GROUP BY status", byStatus);
        Map<String, Long> bySeverity = zeroFilled(Severity.values());
        fill("SELECT severity AS k, count(*) AS c FROM need_requests GROUP BY severity", bySeverity);
        long openCritical = one("""
                SELECT count(*) FROM need_requests
                WHERE severity = 'CRITICAL' AND status IN ('PENDING','VERIFIED','APPROVED')
                """);

        List<DashboardResponse.StockSummary> stock = jdbc.sql("""
                SELECT r.id, r.name, r.unit,
                       coalesce(sum(i.quantity_on_hand), 0) AS on_hand,
                       coalesce(sum(i.quantity_reserved), 0) AS reserved,
                       coalesce(sum(i.quantity_on_hand - i.quantity_reserved), 0) AS available,
                       coalesce(sum(i.quantity_on_hand) FILTER (WHERE i.expiry_date >= current_date
                                                                  AND i.expiry_date <= current_date + 30), 0) AS expiring,
                       coalesce(sum(i.quantity_on_hand) FILTER (WHERE i.expiry_date < current_date), 0) AS expired
                FROM resources r LEFT JOIN inventory i ON i.resource_id = r.id
                WHERE r.active GROUP BY r.id, r.name, r.unit ORDER BY lower(r.name)
                """)
                .query((rs, n) -> new DashboardResponse.StockSummary(rs.getLong("id"), rs.getString("name"),
                        rs.getString("unit"), rs.getLong("on_hand"), rs.getLong("reserved"), rs.getLong("available"),
                        rs.getLong("expiring"), rs.getLong("expired")))
                .list();

        DashboardResponse.ShelterSummary shelters = jdbc.sql("""
                SELECT count(*) FILTER (WHERE status = 'OPEN') AS open_count,
                       coalesce(sum(capacity_people) FILTER (WHERE status = 'OPEN'), 0) AS cap,
                       coalesce(sum(current_occupancy) FILTER (WHERE status = 'OPEN'), 0) AS occ,
                       count(*) FILTER (WHERE status = 'OPEN' AND current_occupancy > capacity_people) AS over_count
                FROM shelters
                """)
                .query((rs, n) -> new DashboardResponse.ShelterSummary(rs.getLong("open_count"), rs.getLong("cap"),
                        rs.getLong("occ"), rs.getLong("over_count")))
                .single();

        List<DashboardResponse.Activity> activity = jdbc.sql("""
                SELECT h.request_id, h.action, h.to_status, u.full_name, h.reason, h.created_at
                FROM request_history h JOIN users u ON u.id = h.actor_user_id
                ORDER BY h.created_at DESC, h.id DESC LIMIT 10
                """)
                .query((rs, n) -> new DashboardResponse.Activity(rs.getLong("request_id"), rs.getString("action"),
                        rs.getString("to_status"), rs.getString("full_name"), rs.getString("reason"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();

        return new DashboardResponse(activeDisasters, population, byStatus, bySeverity, openCritical, stock, shelters,
                activity);
    }

    private long one(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static Map<String, Long> zeroFilled(Enum<?>[] values) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Enum<?> value : values) {
            map.put(value.name(), 0L);
        }
        return map;
    }

    private void fill(String sql, Map<String, Long> target) {
        jdbc.sql(sql).query((rs, n) -> {
            target.put(rs.getString("k"), rs.getLong("c"));
            return rs.getString("k");
        }).list();
    }
}
