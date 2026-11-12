package com.disasterrelief.backend.disaster;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import com.disasterrelief.backend.security.AdminOrOfficer;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.StaffOnly;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/disasters. Read: staff. Create/update: ADMIN or OFFICER. See docs/API_CONTRACT.md section 3 and 8. */
@RestController
@RequestMapping("/api/disasters")
public class DisasterController {

    private final DisasterService service;

    public DisasterController(DisasterService service) {
        this.service = service;
    }

    @GetMapping
    @StaffOnly
    public PageResponse<DisasterResponse> list(@RequestParam(required = false) DisasterStatus status,
                                               @RequestParam(required = false) Severity severity,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        return service.list(status, severity, page, size, sort);
    }

    @GetMapping("/{id}")
    @StaffOnly
    public DisasterResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @AdminOrOfficer
    public ResponseEntity<DisasterResponse> create(@Valid @RequestBody DisasterRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        DisasterResponse created = service.create(request, CurrentUser.id(jwt));
        return ResponseEntity.created(URI.create("/api/disasters/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @AdminOrOfficer
    public DisasterResponse update(@PathVariable long id, @Valid @RequestBody DisasterRequest request) {
        return service.update(id, request);
    }
}
