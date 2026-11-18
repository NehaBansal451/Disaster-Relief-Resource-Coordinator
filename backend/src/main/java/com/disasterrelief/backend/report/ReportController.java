package com.disasterrelief.backend.report;

import com.disasterrelief.backend.security.AdminOrOfficer;
import com.disasterrelief.backend.security.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/reports/situation.pdf: ADMIN and OFFICER only (assumption N1). */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/situation.pdf")
    @AdminOrOfficer
    public ResponseEntity<byte[]> situation(@RequestParam(required = false) Long disasterId, @AuthenticationPrincipal Jwt jwt) {
        byte[] pdf = service.situationPdf(disasterId, CurrentUser.id(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("situation-report.pdf").build().toString())
                .body(pdf);
    }
}
