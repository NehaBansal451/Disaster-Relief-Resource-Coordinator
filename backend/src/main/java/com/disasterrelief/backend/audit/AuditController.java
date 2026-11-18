package com.disasterrelief.backend.audit;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/audit: who did what and when. ADMIN and OFFICER only. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final Map<String, String> SORTS = Map.of("createdAt", "a.created_at", "action", "a.action");

    private static final RowMapper<AuditEntry> MAPPER = (rs, n) -> new AuditEntry(rs.getLong("id"),
            rs.getLong("actor_user_id"), rs.getString("full_name"), rs.getString("actor_role"), rs.getString("action"),
            rs.getString("entity_type"), rs.getObject("entity_id", Long.class), rs.getString("details"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public AuditController(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
    public PageResponse<AuditEntry> list(@RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) Long entityId,
                                         @RequestParam(required = false) Long actorId,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.parse(page, size, sort, SORTS, "createdAt", true);
        String where = " WHERE (CAST(:type AS varchar) IS NULL OR a.entity_type = :type)"
                + " AND (CAST(:eid AS bigint) IS NULL OR a.entity_id = :eid)"
                + " AND (CAST(:actor AS bigint) IS NULL OR a.actor_user_id = :actor)";
        long total = jdbc.sql("SELECT count(*) FROM audit_logs a" + where)
                .param("type", entityType).param("eid", entityId).param("actor", actorId).query(Long.class).single();
        List<AuditEntry> rows = jdbc.sql("""
                SELECT a.id, a.actor_user_id, u.full_name, a.actor_role, a.action, a.entity_type, a.entity_id,
                       a.details, a.created_at
                FROM audit_logs a JOIN users u ON u.id = a.actor_user_id
                """ + where + " ORDER BY " + query.orderBySql() + ", a.id DESC LIMIT :limit OFFSET :offset")
                .param("type", entityType).param("eid", entityId).param("actor", actorId)
                .param("limit", query.size()).param("offset", query.offset()).query(MAPPER).list();
        return PageResponse.of(rows, query, total);
    }
}
