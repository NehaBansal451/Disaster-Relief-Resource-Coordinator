package com.disasterrelief.backend.priority;

import java.math.BigDecimal;

/**
 * The documented, configurable scoring policy (blueprint 7.1). PROPOSED values, pending approval by both owners:
 * weights 0.40 / 0.30 / 0.15 / 0.15; population saturates at 5000 people; waiting time saturates at 72 hours;
 * categories CRITICAL >= 80, HIGH >= 60, MEDIUM >= 40, otherwise LOW.
 */
public record PriorityPolicy(double weightSeverity, double weightPopulation, double weightWaiting,
                             double weightTimeSensitivity, int populationCap, int waitingCapHours,
                             int criticalAt, int highAt, int mediumAt) {

    public static PriorityPolicy defaults() {
        return new PriorityPolicy(0.40, 0.30, 0.15, 0.15, 5000, 72, 80, 60, 40);
    }

    public String categoryFor(BigDecimal score) {
        double s = score.doubleValue();
        if (s >= criticalAt) {
            return "CRITICAL";
        }
        if (s >= highAt) {
            return "HIGH";
        }
        return s >= mediumAt ? "MEDIUM" : "LOW";
    }
}
