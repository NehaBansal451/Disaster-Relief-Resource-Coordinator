package com.disasterrelief.backend.priority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PriorityCalculatorTest {

    private static final PriorityPolicy POLICY = PriorityPolicy.defaults();
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 16, 12, 0, 0, 0, ZoneOffset.UTC);

    private static PriorityResult score(String severity, int people, int hoursWaiting, String category) {
        return PriorityCalculator.calculate(POLICY, severity, people, NOW.minusHours(hoursWaiting), category, NOW);
    }

    @Test
    void maximumInputsGive100AndCritical() {
        PriorityResult r = score("CRITICAL", 5000, 72, "WATER");
        assertEquals(new BigDecimal("100.00"), r.score());
        assertEquals("CRITICAL", r.category());
    }

    @Test
    void minimumInputsStayLow() {
        PriorityResult r = score("LOW", 0, 0, "OTHER");
        assertEquals(new BigDecimal("16.00"), r.score());
        assertEquals("LOW", r.category());
    }

    @Test
    void valuesAboveTheCapsAreCappedAt100() {
        assertEquals(new BigDecimal("100.00"), score("CRITICAL", 900_000, 5000, "MEDICAL").score());
    }

    @Test
    void thresholdBoundaries() {
        assertEquals("HIGH", score("HIGH", 2500, 0, "WATER").category());
        assertEquals(new BigDecimal("60.00"), score("HIGH", 2500, 0, "WATER").score());
        PriorityResult justBelow = score("HIGH", 2450, 0, "WATER");
        assertEquals(new BigDecimal("59.70"), justBelow.score());
        assertEquals("MEDIUM", justBelow.category());
        assertEquals("CRITICAL", POLICY.categoryFor(new BigDecimal("80.00")));
        assertEquals("HIGH", POLICY.categoryFor(new BigDecimal("79.99")));
        assertEquals("MEDIUM", POLICY.categoryFor(new BigDecimal("40.00")));
        assertEquals("LOW", POLICY.categoryFor(new BigDecimal("39.99")));
    }

    @Test
    void sameInputsAlwaysGiveTheSameResult() {
        assertEquals(score("HIGH", 1234, 5, "FOOD"), score("HIGH", 1234, 5, "FOOD"));
    }

    @Test
    void waitingTimeRaisesTheScore() {
        assertTrue(score("MEDIUM", 100, 48, "FOOD").score().compareTo(score("MEDIUM", 100, 1, "FOOD").score()) > 0);
    }

    @Test
    void missingPopulationIsExplainedNotHidden() {
        PriorityResult r = score("MEDIUM", 0, 0, "FOOD");
        assertTrue(r.reasons().get(1).startsWith("People affected not given"));
        assertEquals(5, r.reasons().size());
    }

    @Test
    void everyComponentIsExplainedWithItsWeight() {
        PriorityResult r = score("HIGH", 1800, 6, "WATER");
        assertTrue(r.reasons().get(0).contains("Severity HIGH: 75 of 100 (weight 40%)"));
        assertTrue(r.reasons().get(1).contains("People affected 1800"));
        assertTrue(r.reasons().get(4).startsWith("Total "));
    }

    @Test
    void unknownSeverityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> score("EXTREME", 1, 1, "FOOD"));
    }

    @Test
    void unknownResourceCategoryUsesTheLowestTimeSensitivity() {
        assertEquals(PriorityCalculator.timeSensitivityPoints("OTHER"), PriorityCalculator.timeSensitivityPoints("SOMETHING_NEW"));
        assertEquals(PriorityCalculator.timeSensitivityPoints("OTHER"), PriorityCalculator.timeSensitivityPoints(null));
    }

    @Test
    void futureCreationTimeCountsAsZeroWaiting() {
        PriorityResult r = PriorityCalculator.calculate(POLICY, "LOW", 0, NOW.plusHours(3), "OTHER", NOW);
        assertEquals(new BigDecimal("16.00"), r.score());
    }
}
