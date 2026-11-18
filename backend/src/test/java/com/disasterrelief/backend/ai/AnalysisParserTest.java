package com.disasterrelief.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AnalysisParserTest {

    private static final String OK = "{\"severity\":\"high\",\"summary\":\"Flooded village needs water.\","
            + "\"needs\":[{\"resource\":\"Drinking water\",\"quantity\":500,\"urgency\":\"CRITICAL\"},"
            + "{\"resource\":\"Blankets\",\"quantity\":null,\"urgency\":\"LOW\"}],\"uncertainties\":[\"Exact count unknown\"]}";

    @Test
    void parsesValidAnswerAndNormalizesCase() {
        ReportAnalysis a = AnalysisParser.parse(OK);
        assertEquals("HIGH", a.severity());
        assertEquals(2, a.needs().size());
        assertEquals(500, a.needs().get(0).quantity());
        assertNull(a.needs().get(1).quantity());
        assertEquals(1, a.uncertainties().size());
    }

    @Test
    void ignoresTextAndCodeFencesAroundTheJson() {
        ReportAnalysis a = AnalysisParser.parse("Here you go:\n```json\n" + OK + "\n```\nHope it helps");
        assertEquals("HIGH", a.severity());
    }

    @Test
    void refusesUnknownSeverityOrUrgency() {
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("\"high\"", "\"EXTREME\"")));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("\"LOW\"", "\"soonish\"")));
    }

    @Test
    void refusesBadQuantities() {
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("500", "2.5")));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("500", "0")));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("500", "\"many\"")));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("500", "99999999")));
    }

    @Test
    void refusesMissingSummaryAndNonJson() {
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(OK.replace("\"summary\"", "\"other\"")));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse("I cannot help with that."));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(""));
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(null));
    }

    @Test
    void refusesTooManyNeeds() {
        StringBuilder needs = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            needs.append(i > 0 ? "," : "").append("{\"resource\":\"R").append(i).append("\",\"quantity\":1,\"urgency\":\"LOW\"}");
        }
        String json = "{\"severity\":\"LOW\",\"summary\":\"s\",\"needs\":[" + needs + "],\"uncertainties\":[]}";
        assertThrows(AnalysisFormatException.class, () -> AnalysisParser.parse(json));
    }

    @Test
    void toMapRoundTripsThroughJson() {
        ReportAnalysis a = AnalysisParser.parse(OK);
        Object back = SimpleJson.parse(SimpleJson.write(a.toMap()));
        assertEquals("HIGH", ((java.util.Map<?, ?>) back).get("severity"));
    }
}
