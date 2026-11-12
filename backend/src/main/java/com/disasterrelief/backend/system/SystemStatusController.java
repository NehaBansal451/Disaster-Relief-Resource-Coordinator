package com.disasterrelief.backend.system;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connectivity endpoint. Day 3: restricted to ADMIN, because it reveals the PostGIS and migration versions.
 * Unauthenticated liveness checks should use GET /actuator/health (status only).
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final SystemStatusService service;

    public SystemStatusController(SystemStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemStatusResponse status() {
        return service.currentStatus();
    }
}
