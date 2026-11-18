package com.disasterrelief.backend.allocation;

import com.disasterrelief.backend.allocation.AllocationDtos.AllocationResponse;
import com.disasterrelief.backend.allocation.AllocationDtos.ApproveRequest;
import com.disasterrelief.backend.allocation.AllocationDtos.NoteRequest;
import com.disasterrelief.backend.allocation.AllocationDtos.RecommendRequest;
import com.disasterrelief.backend.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/allocations. Recommend, approve, reject: OFFICER only (ADMIN excluded, O1). Read: ADMIN, OFFICER, WAREHOUSE_MANAGER. */
@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService service;

    public AllocationController(AllocationService service) {
        this.service = service;
    }

    @PostMapping("/recommendations")
    @PreAuthorize("hasRole('OFFICER')")
    public ResponseEntity<AllocationResponse> recommend(@Valid @RequestBody RecommendRequest body,
                                                        @AuthenticationPrincipal Jwt jwt) {
        AllocationResponse created = service.recommend(body.requestId(), CurrentUser.id(jwt));
        return ResponseEntity.created(URI.create("/api/allocations/" + created.id())).body(created);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('OFFICER')")
    public AllocationResponse approve(@PathVariable long id, @Valid @RequestBody ApproveRequest body,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.approve(id, body, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('OFFICER')")
    public AllocationResponse reject(@PathVariable long id, @Valid @RequestBody(required = false) NoteRequest body,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.reject(id, body == null ? null : body.note(), CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER')")
    public AllocationResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER')")
    public List<AllocationResponse> forRequest(@RequestParam long requestId) {
        return service.forRequest(requestId);
    }
}
