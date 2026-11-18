package com.disasterrelief.backend.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns the model's text into a validated {@link ReportAnalysis}; anything outside the schema is rejected, never guessed. */
final class AnalysisParser {

    static final Set<String> LEVELS = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    static final int MAX_NEEDS = 20;
    static final int MAX_UNCERTAINTIES = 10;

    private AnalysisParser() {
    }

    static ReportAnalysis parse(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            throw new AnalysisFormatException("empty answer");
        }
        int start = modelText.indexOf('{');
        int end = modelText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AnalysisFormatException("no JSON object in the answer");
        }
        Object root;
        try {
            root = SimpleJson.parse(modelText.substring(start, end + 1));
        } catch (IllegalArgumentException ex) {
            throw new AnalysisFormatException(ex.getMessage());
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new AnalysisFormatException("answer is not a JSON object");
        }
        String severity = level(map.get("severity"), "severity");
        String summary = text(map.get("summary"), "summary", 1000);
        if (summary.isBlank()) {
            throw new AnalysisFormatException("summary is empty");
        }
        List<ReportAnalysis.Need> needs = new ArrayList<>();
        for (Object item : list(map.get("needs"), "needs", MAX_NEEDS)) {
            if (!(item instanceof Map<?, ?> n)) {
                throw new AnalysisFormatException("a need is not an object");
            }
            String resource = text(n.get("resource"), "resource", 100);
            if (resource.isBlank()) {
                throw new AnalysisFormatException("a need has no resource");
            }
            needs.add(new ReportAnalysis.Need(resource, quantity(n.get("quantity")), level(n.get("urgency"), "urgency")));
        }
        List<String> uncertainties = new ArrayList<>();
        for (Object item : list(map.get("uncertainties"), "uncertainties", MAX_UNCERTAINTIES)) {
            uncertainties.add(text(item, "uncertainty", 300));
        }
        return new ReportAnalysis(severity, summary, needs, uncertainties);
    }

    private static String level(Object value, String field) {
        String v = value instanceof String s ? s.trim().toUpperCase() : "";
        if (!LEVELS.contains(v)) {
            throw new AnalysisFormatException(field + " must be CRITICAL, HIGH, MEDIUM or LOW");
        }
        return v;
    }

    private static String text(Object value, String field, int max) {
        if (!(value instanceof String s)) {
            throw new AnalysisFormatException(field + " must be text");
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static List<?> list(Object value, String field, int max) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> l) || l.size() > max) {
            throw new AnalysisFormatException(field + " must be a list of at most " + max + " items");
        }
        return l;
    }

    private static Integer quantity(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Double d) || d != Math.rint(d) || d < 1 || d > 1_000_000) {
            throw new AnalysisFormatException("quantity must be a whole number from 1 to 1000000, or null");
        }
        return d.intValue();
    }
}
