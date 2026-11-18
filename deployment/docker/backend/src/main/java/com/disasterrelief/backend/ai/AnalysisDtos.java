package com.disasterrelief.backend.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;

public final class AnalysisDtos {

    public static final String ADVISORY = "AI suggestion, not verified. A person must check it and create or verify any request.";

    private AnalysisDtos() {
    }

    public record AnalyzeRequest(@NotBlank @Size(max = 5000) String text) {
    }

    public record ReviewRequest(@NotBlank String decision) {
    }

    public record AnalysisResponse(long id, String status, String reviewStatus, String errorCode, String model,
                                   String text, Map<String, Object> result, String advisory,
                                   OffsetDateTime createdAt, OffsetDateTime reviewedAt) {
    }
}
