package com.disasterrelief.backend.dispatch;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.CreateRequest;
import com.disasterrelief.backend.dispatch.DispatchDtos.DispatchResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.StatusRequest;
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

/** /api/dispatches. Create: WAREHOUSE_MANAGER (own warehouse) or ADMIN. Status: WAREHOUSE_MANAGER (own), FIELD_WORKER, OFFICER, ADMIN. Read: staff. */
@RestController
@RequestMapping("/api/dispatches")
public class DispatchController {

    private final DispatchService service;

    public DispatchController(DispatchService service) {
        this.service = service;
    }

    @GetMapping
    @StaffOnly
    public PageResponse<DispatchResponse> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long requestId,
                                               @RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        return service.list(status, requestId, warehouseId, page, size, sort);
    }

    @GetMapping("/{id}")
    @StaffOnly
    public DispatchResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<DispatchResponse> create(@Valid @RequestBody CreateRequest body, @AuthenticationPrincipal Jwt jwt) {
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        DispatchResponse created = service.create(body, CurrentUser.id(jwt), admin);
        return ResponseEntity.created(URI.create("/api/dispatches/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER','FIELD_WORKER')")
    public DispatchResponse status(@PathVariable long id, @Valid @RequestBody StatusRequest body,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.updateStatus(id, body, CurrentUser.id(jwt), jwt.getClaimAsString("role"));
    }
}
