package com.disasterrelief.backend.volunteer;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.JwtService;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignmentResponse;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignmentStatusRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AvailabilityRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.CreateRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.VolunteerResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
 * Volunteers (ASSUMPTION V1, needs approval): ADMIN, OFFICER and NGO register and list volunteers (phone numbers are
 * personal data); only ADMIN and OFFICER assign work; a VOLUNTEER can see and update only their own linked record.
 */
@RestController
@RequestMapping("/api")
public class VolunteerController {

    private static final String STAFF = "hasAnyRole('ADMIN','OFFICER','NGO')";
    private static final String STAFF_OR_VOLUNTEER = "hasAnyRole('ADMIN','OFFICER','NGO','VOLUNTEER')";

    private final VolunteerService service;

    public VolunteerController(VolunteerService service) {
        this.service = service;
    }

    @PostMapping("/volunteers")
    @PreAuthorize(STAFF)
    public ResponseEntity<VolunteerResponse> create(@Valid @RequestBody CreateRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body, CurrentUser.id(jwt)));
    }

    @GetMapping("/volunteers")
    @PreAuthorize(STAFF)
    public PageResponse<VolunteerResponse> list(@RequestParam(required = false) String availability,
                                                @RequestParam(required = false) String district,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String sort) {
        return service.list(availability, district, page, size, sort);
    }

    @GetMapping("/volunteers/{id}")
    @PreAuthorize(STAFF)
    public VolunteerResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PatchMapping("/volunteers/{id}/availability")
    @PreAuthorize(STAFF_OR_VOLUNTEER)
    public VolunteerResponse availability(@PathVariable long id, @Valid @RequestBody AvailabilityRequest body,
                                          @AuthenticationPrincipal Jwt jwt) {
        return service.setAvailability(id, body.availability(), CurrentUser.id(jwt), role(jwt));
    }

    @PostMapping("/volunteers/{id}/assignments")
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
    public ResponseEntity<AssignmentResponse> assign(@PathVariable long id, @Valid @RequestBody AssignRequest body,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(id, body, CurrentUser.id(jwt)));
    }

    @GetMapping("/volunteers/{id}/assignments")
    @PreAuthorize(STAFF_OR_VOLUNTEER)
    public List<AssignmentResponse> assignments(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return service.assignments(id, CurrentUser.id(jwt), role(jwt));
    }

    @PatchMapping("/assignments/{id}/status")
    @PreAuthorize(STAFF_OR_VOLUNTEER)
    public AssignmentResponse move(@PathVariable long id, @Valid @RequestBody AssignmentStatusRequest body,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.moveAssignment(id, body.status(), CurrentUser.id(jwt), role(jwt));
    }

    private static String role(Jwt jwt) {
        return jwt.getClaimAsString(JwtService.ROLE_CLAIM);
    }
}
