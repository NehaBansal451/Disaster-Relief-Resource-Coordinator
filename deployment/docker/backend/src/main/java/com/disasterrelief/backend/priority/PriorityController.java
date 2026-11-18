package com.disasterrelief.backend.priority;

import com.disasterrelief.backend.request.RequestResponse;
import com.disasterrelief.backend.request.RequestService;
import com.disasterrelief.backend.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Priority endpoints (officer only): recalculate, and override the category with a recorded reason. */
@RestController
@RequestMapping("/api/requests")
public class PriorityController {

    private final PriorityService priority;
    private final RequestService requests;

    public PriorityController(PriorityService priority, RequestService requests) {
        this.priority = priority;
        this.requests = requests;
    }

    @PostMapping("/{id}/priority")
    @PreAuthorize("hasRole('OFFICER')")
    public RequestResponse recalculate(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        priority.calculate(id, CurrentUser.id(jwt));
        return requests.get(id);
    }

    @PatchMapping("/{id}/priority-override")
    @PreAuthorize("hasRole('OFFICER')")
    public RequestResponse override(@PathVariable long id, @Valid @RequestBody OverrideRequest body,
                                    @AuthenticationPrincipal Jwt jwt) {
        priority.override(id, body.category(), body.reason(), CurrentUser.id(jwt));
        return requests.get(id);
    }
}
