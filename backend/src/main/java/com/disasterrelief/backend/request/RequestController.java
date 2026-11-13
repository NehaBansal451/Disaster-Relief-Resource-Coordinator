package com.disasterrelief.backend.request;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.StaffOnly;
import jakarta.validation.Valid;
import java.net.URI;
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

/**
 * /api/requests (docs/API_CONTRACT.md sections 1 and 3).
 * ASSUMPTIONS pending approval: CITIZEN cannot submit yet (O4); ADMIN cannot verify/approve/reject (O1).
 */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @GetMapping
    @StaffOnly
    public PageResponse<RequestResponse> list(@RequestParam(required = false) RequestStatus status,
                                              @RequestParam(required = false) Severity severity,
                                              @RequestParam(required = false) Long disasterId,
                                              @RequestParam(required = false) Long areaId,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String sort) {
        return service.list(status, severity, disasterId, areaId, page, size, sort);
    }

    @GetMapping("/{id}")
    @StaffOnly
    public RequestResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OFFICER','FIELD_WORKER','NGO')")
    public ResponseEntity<RequestResponse> submit(@Valid @RequestBody NeedRequestCreate body,
                                                  @AuthenticationPrincipal Jwt jwt) {
        RequestResponse created = service.submit(body, CurrentUser.id(jwt));
        return ResponseEntity.created(URI.create("/api/requests/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasRole('OFFICER')")
    public RequestResponse verify(@PathVariable long id, @Valid @RequestBody VerifyRequest body,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.verify(id, body, CurrentUser.id(jwt));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('OFFICER')")
    public RequestResponse approve(@PathVariable long id, @Valid @RequestBody(required = false) NoteRequest body,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.approve(id, body, CurrentUser.id(jwt));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('OFFICER')")
    public RequestResponse reject(@PathVariable long id, @Valid @RequestBody NoteRequest body,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.reject(id, body, CurrentUser.id(jwt));
    }

    /** Officer: any cancellable request. Requester: own request while PENDING (decided in the service). */
    @PatchMapping("/{id}/cancel")
    @StaffOnly
    public RequestResponse cancel(@PathVariable long id, @Valid @RequestBody NoteRequest body,
                                  @AuthenticationPrincipal Jwt jwt) {
        boolean officer = "OFFICER".equals(jwt.getClaimAsString("role"));
        return service.cancel(id, body, CurrentUser.id(jwt), officer);
    }
}
