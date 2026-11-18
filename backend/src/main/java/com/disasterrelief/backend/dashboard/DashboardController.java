package com.disasterrelief.backend.dashboard;

import com.disasterrelief.backend.security.StaffOnly;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/dashboard: operational totals for staff. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @StaffOnly
    public DashboardResponse get() {
        return service.build();
    }
}
