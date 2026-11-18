package com.disasterrelief.backend.ai;

import com.disasterrelief.backend.ai.AnalysisDtos.AnalysisResponse;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AnalysisRepository {

    static final Map<String, String> SORTS = Map.of("createdAt", "a.created_at");

    private static final RowMapper<AnalysisResponse> MAPPER = (rs, n) -> {
        String json = rs.getString("result_json");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = json == null ? null : (Map<String, Object>) SimpleJson.parse(json);
        return new AnalysisResponse(rs.getLong("id"), rs.getString("status"), rs.getString("review_status"),
                rs.getString("error_code"), rs.getString("model"), rs.getString("report_text"), result,
                AnalysisDtos.ADVISORY, rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("reviewed_at", OffsetDateTime.class));
    };

    private final JdbcClient jdbc;

    AnalysisRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    long insert(String scrubbedText, String model, String status, String resultJson, String errorCode, long userId) {
        return jdbc.sql("""
                INSERT INTO ai_analyses (report_text, model, status, result_json, error_code, created_by)
                VALUES (:text, :model, :status, :json, :err, :by) RETURNING id
                """)
                .param("text", scrubbedText).param("model", model).param("status", status).param("json", resultJson)
                .param("err", errorCode).param("by", userId).query(Long.class).single();
    }

    Optional<AnalysisResponse> find(long id) {
        return jdbc.sql("SELECT a.* FROM ai_analyses a WHERE a.id = :id").param("id", id).query(MAPPER).optional();
    }

    PageResponse<AnalysisResponse> search(String reviewStatus, PageQuery page) {
        String where = " WHERE (CAST(:rs AS varchar) IS NULL OR a.review_status = :rs)";
        long total = jdbc.sql("SELECT count(*) FROM ai_analyses a" + where).param("rs", reviewStatus)
                .query(Long.class).single();
        List<AnalysisResponse> rows = jdbc.sql("SELECT a.* FROM ai_analyses a" + where + " ORDER BY "
                        + page.orderBySql() + ", a.id DESC LIMIT :limit OFFSET :offset")
                .param("rs", reviewStatus).param("limit", page.size()).param("offset", page.offset())
                .query(MAPPER).list();
        return PageResponse.of(rows, page, total);
    }

    /** Race-safe: only a SUCCEEDED, still UNREVIEWED analysis can be reviewed, and only once. */
    boolean review(long id, String decision, long reviewerId) {
        return jdbc.sql("""
                UPDATE ai_analyses SET review_status = :d, reviewed_by = :by, reviewed_at = now()
                WHERE id = :id AND status = 'SUCCEEDED' AND review_status = 'UNREVIEWED'
                """).param("d", decision).param("by", reviewerId).param("id", id).update() > 0;
    }
}
