package com.disasterrelief.backend.partner;

import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.partner.PartnerDtos.ContributionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PartnerRepository {

    static final Map<String, String> SORTS = Map.of("createdAt", "c.created_at", "quantity", "c.quantity",
            "status", "c.status");

    private static final String SELECT = """
            SELECT c.id, c.organization_id, o.name AS organization_name, c.resource_id, r.name AS resource_name,
                   c.quantity, c.description, c.status, c.created_at, c.updated_at
            FROM partner_contributions c JOIN organizations o ON o.id = c.organization_id
                 JOIN resources r ON r.id = c.resource_id
            """;

    private static final RowMapper<ContributionResponse> MAPPER = (rs, n) -> new ContributionResponse(rs.getLong("id"),
            rs.getLong("organization_id"), rs.getString("organization_name"), rs.getLong("resource_id"),
            rs.getString("resource_name"), rs.getInt("quantity"), rs.getString("description"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public PartnerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public Optional<Long> organizationOfUser(long userId) {
        return jdbc.sql("SELECT organization_id FROM users WHERE id = :id").param("id", userId).query(Long.class).optional();
    }

    public boolean organizationExists(long id) {
        return jdbc.sql("SELECT count(*) FROM organizations WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    public boolean resourceActive(long id) {
        return jdbc.sql("SELECT count(*) FROM resources WHERE id = :id AND active").param("id", id).query(Long.class).single() > 0;
    }

    public long insert(long orgId, long resourceId, int quantity, String description, long actorId) {
        return jdbc.sql("""
                INSERT INTO partner_contributions (organization_id, resource_id, quantity, description, created_by)
                VALUES (:o, :r, :q, :d, :by) RETURNING id
                """)
                .param("o", orgId).param("r", resourceId).param("q", quantity)
                .param("d", description == null || description.isBlank() ? null : description.trim()).param("by", actorId)
                .query(Long.class).single();
    }

    public Optional<ContributionResponse> find(long id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id").param("id", id).query(MAPPER).optional();
    }

    /** @param orgFilter when not null, only that organization's rows (an NGO sees only its own offers) */
    public PageResponse<ContributionResponse> search(Long orgFilter, String status, PageQuery page) {
        String where = " WHERE (CAST(:org AS bigint) IS NULL OR c.organization_id = :org)"
                + " AND (CAST(:status AS varchar) IS NULL OR c.status = :status)";
        long total = jdbc.sql("SELECT count(*) FROM partner_contributions c" + where)
                .param("org", orgFilter).param("status", status).query(Long.class).single();
        List<ContributionResponse> rows = jdbc.sql(SELECT + where + " ORDER BY " + page.orderBySql()
                        + ", c.id DESC LIMIT :limit OFFSET :offset")
                .param("org", orgFilter).param("status", status).param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }

    /** Race-safe: only succeeds while the row is still in the expected status. */
    public boolean move(long id, String from, String to, long actorId) {
        return jdbc.sql("""
                UPDATE partner_contributions SET status = :to, decided_by = :by, updated_at = now()
                WHERE id = :id AND status = :from
                """).param("to", to).param("from", from).param("by", actorId).param("id", id).update() > 0;
    }
}
