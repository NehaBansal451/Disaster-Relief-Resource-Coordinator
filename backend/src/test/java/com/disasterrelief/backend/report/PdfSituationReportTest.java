package com.disasterrelief.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disasterrelief.backend.analytics.AnalyticsDtos.ShortageRow;
import com.disasterrelief.backend.analytics.AnalyticsDtos.Summary;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

class PdfSituationReportTest {

    private static SituationData data(int shortages) {
        Summary summary = new Summary(1L, 12, Map.of("PENDING", 4L, "DELIVERED", 8L), Map.of("CRITICAL", 5L, "LOW", 7L),
                4, 3, 800, 2000, 40.0, 5.5);
        List<ShortageRow> rows = new ArrayList<>();
        for (int i = 0; i < shortages; i++) {
            rows.add(new ShortageRow(i, "Resource " + i, "unit", 2, 100, 40, 60, 40.0, 10));
        }
        return new SituationData("Situation report: flood ਪੱਟ", OffsetDateTime.now(), "Officer",
                summary, rows, List.of(new SituationData.TopRequest(1, "Blanket", 50, "CRITICAL", "HIGH", "Area", "APPROVED")));
    }

    @Test
    void producesAReadablePdfEvenWithNonLatinText() throws Exception {
        byte[] pdf = PdfSituationReport.render(data(3));
        assertEquals("%PDF-", new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 1);
        }
    }

    @Test
    void longReportsContinueOnFurtherPages() throws Exception {
        try (PDDocument doc = Loader.loadPDF(PdfSituationReport.render(data(120)))) {
            assertTrue(doc.getNumberOfPages() > 1);
        }
    }

    @Test
    void nonLatinCharactersBecomeQuestionMarks() {
        assertEquals("a?b", PdfSituationReport.latin("aਪb"));
        assertEquals("café", PdfSituationReport.latin("café"));
    }
}
