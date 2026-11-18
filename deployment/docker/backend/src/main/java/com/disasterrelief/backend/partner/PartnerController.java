package com.disasterrelief.backend.partner;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.partner.PartnerDtos.ContributionResponse;
import com.disasterrelief.backend.partner.PartnerDtos.DecisionRequest;
import com.disasterrelief.backend.partner.PartnerDtos.OfferRequest;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.JwtService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/partner-contributions")
public class PartnerController {

    private final PartnerService service;

    public PartnerController(PartnerService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','NGO')")
    public ResponseEntity<ContributionResponse> offer(@Valid @RequestBody OfferRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.offer(body, CurrentUser.id(jwt), jwt.getClaimAsString(JwtService.ROLE_CLAIM)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','NGO')")
    public PageResponse<ContributionResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return service.list(status, page, size, sort, CurrentUser.id(jwt), jwt.getClaimAsString(JwtService.ROLE_CLAIM));
    }

    /** status: ACCEPTED or DECLINED (from OFFERED), or RECEIVED (from ACCEPTED). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
    public ContributionResponse move(@PathVariable long id, @Valid @RequestBody DecisionRequest body,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.move(id, body.status(), CurrentUser.id(jwt));
    }
}
