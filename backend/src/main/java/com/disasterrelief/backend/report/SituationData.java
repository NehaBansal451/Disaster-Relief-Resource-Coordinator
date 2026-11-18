package com.disasterrelief.backend.report;

import com.disasterrelief.backend.analytics.AnalyticsDtos.ShortageRow;
import com.disasterrelief.backend.analytics.AnalyticsDtos.Summary;
import java.time.OffsetDateTime;
import java.util.List;

/** Everything the situation report prints, read from the database at one moment. */
public record SituationData(String title, OffsetDateTime generatedAt, String generatedBy, Summary summary,
                            List<ShortageRow> shortages, List<TopRequest> topRequests) {

    public record TopRequest(long id, String resource, int quantity, String severity, String priority,
                             String area, String status) {
    }
}
