package com.disasterrelief.backend.ai;

import com.disasterrelief.backend.ai.AnalysisDtos.AnalysisResponse;
import com.disasterrelief.backend.ai.AnalysisDtos.AnalyzeRequest;
import com.disasterrelief.backend.ai.AnalysisDtos.ReviewRequest;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.security.AdminOrOfficer;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.StaffOnly;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** /api/ai/analyses: any staff role may ask for an analysis; only ADMIN or OFFICER may review (accept/reject) one. */
@RestController
@RequestMapping("/api/ai/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping
    @StaffOnly
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalyzeRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.analyze(body.text(), CurrentUser.id(jwt)));
    }

    @GetMapping
    @StaffOnly
    public PageResponse<AnalysisResponse> list(@RequestParam(required = false) String reviewStatus,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        return service.list(reviewStatus, page, size, sort);
    }

    @GetMapping("/{id}")
    @StaffOnly
    public AnalysisResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/review")
    @AdminOrOfficer
    public AnalysisResponse review(@PathVariable long id, @Valid @RequestBody ReviewRequest body, @AuthenticationPrincipal Jwt jwt) {
        return service.review(id, body.decision(), CurrentUser.id(jwt));
    }
}
