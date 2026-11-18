package com.disasterrelief.backend.shelter;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.security.AdminOrOfficer;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.StaffOnly;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/shelters. Read: staff. Create: ADMIN, OFFICER. Occupancy update: ADMIN, OFFICER, FIELD_WORKER (assumption S2). */
@RestController
@RequestMapping("/api/shelters")
public class ShelterController {

    private final ShelterService service;

    public ShelterController(ShelterService service) {
        this.service = service;
    }

    @GetMapping
    @StaffOnly
    public PageResponse<ShelterResponse> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Boolean overcrowdedOnly,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String sort) {
        return service.list(status, overcrowdedOnly, page, size, sort);
    }

    @GetMapping("/{id}")
    @StaffOnly
    public ShelterResponse get(@PathVariable long id) {
        return service.get(id);
    }

    /** Nearest other open shelters with free beds, for an overcrowded shelter. */
    @GetMapping("/{id}/alternatives")
    @StaffOnly
    public List<ShelterResponse> alternatives(@PathVariable long id) {
        return service.alternatives(id);
    }

    @PostMapping
    @AdminOrOfficer
    public ResponseEntity<ShelterResponse> create(@Valid @RequestBody ShelterRequest body, @AuthenticationPrincipal Jwt jwt) {
        ShelterResponse created = service.create(body, CurrentUser.id(jwt));
        return ResponseEntity.created(URI.create("/api/shelters/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/occupancy")
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','FIELD_WORKER')")
    public ShelterResponse occupancy(@PathVariable long id, @Valid @RequestBody OccupancyRequest body) {
        return service.updateOccupancy(id, body);
    }
}
