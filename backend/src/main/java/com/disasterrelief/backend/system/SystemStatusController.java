package com.disasterrelief.backend.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Day 2 connectivity endpoint. Public for now; role-based security arrives with authentication (Day 3). */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final SystemStatusService service;

    public SystemStatusController(SystemStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return service.currentStatus();
    }
}
