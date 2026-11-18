package com.disasterrelief.backend.report;

import com.disasterrelief.backend.analytics.AnalyticsDtos.ShortageRow;
import com.disasterrelief.backend.analytics.AnalyticsDtos.Summary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Renders the situation report as a plain, printable A4 PDF with the built-in Helvetica font. That font covers
 * Latin text only, so any other character (for example Devanagari) is printed as '?' rather than failing.
 */
public final class PdfSituationReport {

    private static final float MARGIN = 50;
    private static final float LEADING = 15;
    private static final int WRAP_AT = 95;

    private PdfSituationReport() {
    }

    public static byte[] render(SituationData data) {
        List<Line> lines = layout(data);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDRectangle size = PDRectangle.A4;
            float top = size.getHeight() - MARGIN;
            PDPageContentStream cs = null;
            float y = 0;
            int pageNo = 0;
            for (Line line : lines) {
                if (cs == null || y < MARGIN + LEADING) {
                    if (cs != null) {
                        footer(cs, regular, pageNo);
                        cs.close();
                    }
                    PDPage page = new PDPage(size);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    pageNo++;
                    y = top;
                }
                cs.beginText();
                cs.setFont(line.bold() ? bold : regular, line.size());
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(latin(line.text()));
                cs.endText();
                y -= line.size() + (LEADING - 12);
            }
            if (cs != null) {
                footer(cs, regular, pageNo);
                cs.close();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not build the PDF.", ex);
        }
    }

    private static void footer(PDPageContentStream cs, PDType1Font font, int pageNo) throws IOException {
        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(MARGIN, 30);
        cs.showText("Disaster Relief Resource Coordinator - page " + pageNo);
        cs.endText();
    }

    private record Line(String text, float size, boolean bold) {
    }

    static List<Line> layout(SituationData d) {
        List<Line> out = new ArrayList<>();
        out.add(new Line(d.title(), 18, true));
        out.add(new Line("Generated " + d.generatedAt().toLocalDate() + " " + d.generatedAt().toLocalTime().withNano(0)
                + " by " + d.generatedBy(), 10, false));
        out.add(new Line("", 12, false));
        Summary s = d.summary();
        out.add(new Line("Requests", 13, true));
        out.add(new Line("Total requests: " + s.totalRequests() + "   Awaiting verification: " + s.awaitingVerification()
                + "   Open critical: " + s.openCritical(), 11, false));
        out.add(new Line("By status: " + flatten(s.byStatus()), 11, false));
        out.add(new Line("By severity: " + flatten(s.bySeverity()), 11, false));
        out.add(new Line("Delivered quantity: " + s.deliveredQuantity() + " of " + s.requestedQuantity() + " requested ("
                + s.fulfilmentPercent() + "%)", 11, false));
        out.add(new Line("Average hours from request to delivery: "
                + (s.averageHoursToDeliver() == null ? "no deliveries yet" : String.valueOf(s.averageHoursToDeliver())), 11, false));
        out.add(new Line("", 12, false));
        out.add(new Line("Shortages (unallocated demand against free stock)", 13, true));
        if (d.shortages().isEmpty()) {
            out.add(new Line("No open demand.", 11, false));
        }
        for (ShortageRow r : d.shortages()) {
            String text = r.resource() + " (" + r.unit() + "): demand " + r.demand() + ", available " + r.available()
                    + ", " + (r.shortage() > 0 ? "SHORT BY " + r.shortage() : "covered") + " (" + r.coveragePercent()
                    + "%), partner offers " + r.offeredByPartners();
            wrap(text, out, 11);
        }
        out.add(new Line("", 12, false));
        out.add(new Line("Highest-priority open requests", 13, true));
        if (d.topRequests().isEmpty()) {
            out.add(new Line("None.", 11, false));
        }
        for (SituationData.TopRequest t : d.topRequests()) {
            wrap("#" + t.id() + " " + t.resource() + " x" + t.quantity() + " - " + t.severity()
                    + (t.priority() == null ? "" : ", priority " + t.priority()) + " - " + t.area() + " [" + t.status() + "]", out, 11);
        }
        out.add(new Line("", 12, false));
        out.add(new Line("Figures come from the live database at the time shown. Priority scores are decision support; "
                + "an officer's judgement decides.", 9, false));
        return out;
    }

    private static String flatten(Map<String, Long> map) {
        if (map.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(sb.length() > 0 ? ", " : "").append(k).append(' ').append(v));
        return sb.toString();
    }

    private static void wrap(String text, List<Line> out, float size) {
        String rest = text;
        while (rest.length() > WRAP_AT) {
            int cut = rest.lastIndexOf(' ', WRAP_AT);
            if (cut < 20) {
                cut = WRAP_AT;
            }
            out.add(new Line(rest.substring(0, cut), size, false));
            rest = "  " + rest.substring(cut).trim();
        }
        out.add(new Line(rest, size, false));
    }

    /** Helvetica (WinAnsi) cannot show characters outside Latin-1; replace them so showText never throws. */
    static String latin(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c >= 0x20 && c <= 0x7E ? c : (c >= 0xA0 && c <= 0xFF ? c : '?'));
        }
        return sb.toString();
    }
}
