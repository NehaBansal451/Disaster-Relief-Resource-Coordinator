package com.disasterrelief.backend.alert;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.StaffOnly;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/alerts: every staff role sees all alerts (assumption A1); marking one read records who did it. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Map<String, String> SORTS = Map.of("createdAt", "a.created_at", "severity",
            "CASE a.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END");

    private static final RowMapper<AlertItem> MAPPER = (rs, n) -> new AlertItem(rs.getLong("id"), rs.getString("type"),
            rs.getString("severity"), rs.getString("message"), rs.getString("entity_type"),
            rs.getObject("entity_id", Long.class), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("read_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public AlertController(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @GetMapping
    @StaffOnly
    public PageResponse<AlertItem> list(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size,
                                        @RequestParam(required = false) String sort) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (s != null && !s.equals("NEW") && !s.equals("READ")) {
            throw ApiException.validation("status", "status must be NEW or READ");
        }
        PageQuery query = PageQuery.parse(page, size, sort, SORTS, "createdAt", true);
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR a.status = :status)"
                + " AND (CAST(:type AS varchar) IS NULL OR a.type = :type)";
        long total = jdbc.sql("SELECT count(*) FROM alerts a" + where).param("status", s).param("type", type)
                .query(Long.class).single();
        List<AlertItem> rows = jdbc.sql("SELECT a.* FROM alerts a" + where + " ORDER BY " + query.orderBySql()
                        + ", a.id DESC LIMIT :limit OFFSET :offset")
                .param("status", s).param("type", type).param("limit", query.size()).param("offset", query.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, query, total);
    }

    @PatchMapping("/{id}/read")
    @StaffOnly
    public AlertItem markRead(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        jdbc.sql("UPDATE alerts SET status = 'READ', read_by = :u, read_at = now() WHERE id = :id AND status = 'NEW'")
                .param("u", CurrentUser.id(jwt)).param("id", id).update();
        return jdbc.sql("SELECT a.* FROM alerts a WHERE a.id = :id").param("id", id).query(MAPPER).optional()
                .orElseThrow(() -> ApiException.notFound("Alert " + id + " was not found."));
    }
}
