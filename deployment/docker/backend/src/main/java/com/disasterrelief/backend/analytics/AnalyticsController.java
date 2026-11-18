package com.disasterrelief.backend.analytics;

import com.disasterrelief.backend.analytics.AnalyticsDtos.ShortageRow;
import com.disasterrelief.backend.analytics.AnalyticsDtos.Summary;
import com.disasterrelief.backend.security.AdminOrOfficer;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/analytics: ADMIN and OFFICER only (assumption N1, needs approval). disasterId is optional. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @AdminOrOfficer
    public Summary summary(@RequestParam(required = false) Long disasterId) {
        return service.summary(disasterId);
    }

    @GetMapping("/shortages")
    @AdminOrOfficer
    public List<ShortageRow> shortages(@RequestParam(required = false) Long disasterId) {
        return service.shortages(disasterId);
    }
}
