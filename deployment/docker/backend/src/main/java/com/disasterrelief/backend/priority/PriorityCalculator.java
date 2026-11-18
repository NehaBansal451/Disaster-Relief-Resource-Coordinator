package com.disasterrelief.backend.priority;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, explainable priority (no hidden variables). Every input is normalised to 0-100:
 * severity (CRITICAL 100, HIGH 75, MEDIUM 50, LOW 25); population = people / populationCap, capped at 100;
 * waiting = hours since submission / waitingCapHours, capped at 100; time sensitivity by resource category
 * (WATER 100, MEDICAL 100, RESCUE_EQUIPMENT 90, FOOD 80, SHELTER 60, BEDDING 60, VEHICLE 50, OTHER 40).
 * Same inputs and same "now" always give the same score.
 */
public final class PriorityCalculator {

    private PriorityCalculator() {
    }

    static double severityPoints(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 100;
            case "HIGH" -> 75;
            case "MEDIUM" -> 50;
            case "LOW" -> 25;
            default -> throw new IllegalArgumentException("Unknown severity: " + severity);
        };
    }

    static double timeSensitivityPoints(String resourceCategory) {
        return switch (resourceCategory == null ? "OTHER" : resourceCategory) {
            case "WATER", "MEDICAL" -> 100;
            case "RESCUE_EQUIPMENT" -> 90;
            case "FOOD" -> 80;
            case "SHELTER", "BEDDING" -> 60;
            case "VEHICLE" -> 50;
            default -> 40;
        };
    }

    public static PriorityResult calculate(PriorityPolicy p, String severity, int population, OffsetDateTime createdAt,
                                           String resourceCategory, OffsetDateTime now) {
        double sev = severityPoints(severity);
        double pop = Math.min(100.0, Math.max(0, population) * 100.0 / p.populationCap());
        double hours = Math.max(0, Duration.between(createdAt, now).toMinutes()) / 60.0;
        double wait = Math.min(100.0, hours * 100.0 / p.waitingCapHours());
        double time = timeSensitivityPoints(resourceCategory);

        double total = p.weightSeverity() * sev + p.weightPopulation() * pop + p.weightWaiting() * wait
                + p.weightTimeSensitivity() * time;
        BigDecimal score = BigDecimal.valueOf(Math.min(100.0, Math.max(0.0, total))).setScale(2, RoundingMode.HALF_UP);
        String category = p.categoryFor(score);

        List<String> reasons = new ArrayList<>();
        reasons.add(line("Severity " + severity, sev, p.weightSeverity()));
        reasons.add(line(population > 0 ? "People affected " + population : "People affected not given", pop,
                p.weightPopulation()));
        reasons.add(line(String.format(Locale.ROOT, "Waiting %.1f hours", hours), wait, p.weightWaiting()));
        reasons.add(line("Resource category " + (resourceCategory == null ? "OTHER" : resourceCategory)
                + " time sensitivity", time, p.weightTimeSensitivity()));
        reasons.add(String.format(Locale.ROOT,
                "Total %.2f gives category %s (CRITICAL from %d, HIGH from %d, MEDIUM from %d)", score.doubleValue(),
                category, p.criticalAt(), p.highAt(), p.mediumAt()));
        return new PriorityResult(score, category, List.copyOf(reasons));
    }

    private static String line(String what, double points, double weight) {
        return String.format(Locale.ROOT, "%s: %.0f of 100 (weight %.0f%%)", what, points, weight * 100);
    }
}
