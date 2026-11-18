package com.disasterrelief.backend.report;

import com.disasterrelief.backend.analytics.AnalyticsService;
import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final AnalyticsService analytics;
    private final AuditLogger audit;
    private final JdbcClient jdbc;

    public ReportService(AnalyticsService analytics, AuditLogger audit, JdbcTemplate jdbcTemplate) {
        this.analytics = analytics;
        this.audit = audit;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional
    public byte[] situationPdf(Long disasterId, long userId) {
        String title = "Situation report";
        if (disasterId != null) {
            String name = jdbc.sql("SELECT name FROM disasters WHERE id = :id").param("id", disasterId)
                    .query(String.class).optional()
                    .orElseThrow(() -> ApiException.notFound("Disaster " + disasterId + " was not found."));
            title = "Situation report: " + name;
        } else {
            title = "Situation report: all disasters";
        }
        String by = jdbc.sql("SELECT full_name FROM users WHERE id = :id").param("id", userId)
                .query(String.class).optional().orElse("unknown user");
        List<SituationData.TopRequest> top = jdbc.sql("""
                SELECT n.id, r.name AS resource, n.quantity, n.severity, n.priority_category, a.name AS area, n.status
                FROM need_requests n JOIN resources r ON r.id = n.resource_id JOIN affected_areas a ON a.id = n.affected_area_id
                WHERE n.status IN ('VERIFIED','APPROVED') AND (CAST(:d AS bigint) IS NULL OR n.disaster_id = :d)
                ORDER BY n.priority_score DESC NULLS LAST, n.created_at LIMIT 10
                """).param("d", disasterId)
                .query((rs, i) -> new SituationData.TopRequest(rs.getLong("id"), rs.getString("resource"),
                        rs.getInt("quantity"), rs.getString("severity"), rs.getString("priority_category"),
                        rs.getString("area"), rs.getString("status"))).list();
        SituationData data = new SituationData(title, OffsetDateTime.now(), by, analytics.summary(disasterId),
                analytics.shortages(disasterId), top);
        byte[] pdf = PdfSituationReport.render(data);
        audit.log(userId, "REPORT_GENERATED", "reports", disasterId, "situation pdf");
        return pdf;
    }
}
