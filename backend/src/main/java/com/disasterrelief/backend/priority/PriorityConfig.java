package com.disasterrelief.backend.priority;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Weights and thresholds are configurable (drrc.priority.*) so the team can change them without code changes. */
@Configuration
public class PriorityConfig {

    @Bean
    PriorityPolicy priorityPolicy(
            @Value("${drrc.priority.weight-severity:0.40}") double severity,
            @Value("${drrc.priority.weight-population:0.30}") double population,
            @Value("${drrc.priority.weight-waiting:0.15}") double waiting,
            @Value("${drrc.priority.weight-time-sensitivity:0.15}") double timeSensitivity,
            @Value("${drrc.priority.population-cap:5000}") int populationCap,
            @Value("${drrc.priority.waiting-cap-hours:72}") int waitingCapHours,
            @Value("${drrc.priority.critical-at:80}") int criticalAt,
            @Value("${drrc.priority.high-at:60}") int highAt,
            @Value("${drrc.priority.medium-at:40}") int mediumAt) {
        return new PriorityPolicy(severity, population, waiting, timeSensitivity, populationCap, waitingCapHours,
                criticalAt, highAt, mediumAt);
    }
}
