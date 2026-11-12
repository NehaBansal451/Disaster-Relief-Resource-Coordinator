package com.disasterrelief.backend.affectedarea;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import com.disasterrelief.backend.security.AdminOrOfficer;
import com.disasterrelief.backend.security.StaffOnly;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/affected-areas. Read: staff (filter by disasterId). Create: ADMIN or OFFICER. */
@RestController
@RequestMapping("/api/affected-areas")
public class AffectedAreaController {

    private final AffectedAreaService service;

    public AffectedAreaController(AffectedAreaService service) {
        this.service = service;
    }

    @GetMapping
    @StaffOnly
    public PageResponse<AffectedAreaResponse> list(@RequestParam(required = false) Long disasterId,
                                                   @RequestParam(required = false) Severity impactLevel,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort) {
        return service.list(disasterId, impactLevel, page, size, sort);
    }

    @PostMapping
    @AdminOrOfficer
    public ResponseEntity<AffectedAreaResponse> create(@Valid @RequestBody AffectedAreaRequest request) {
        AffectedAreaResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/affected-areas?disasterId=" + created.disasterId())).body(created);
    }
}
