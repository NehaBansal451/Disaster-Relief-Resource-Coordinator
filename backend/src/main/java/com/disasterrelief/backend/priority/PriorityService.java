package com.disasterrelief.backend.priority;

import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Severity;
import com.disasterrelief.backend.request.RequestRepository;
import com.disasterrelief.backend.request.RequestStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates and overrides request priority. Only VERIFIED or APPROVED requests have a priority (contract). */
@Service
public class PriorityService {

    private final PriorityRepository repository;
    private final RequestRepository requests;
    private final PriorityPolicy policy;
    private final AuditLogger audit;

    public PriorityService(PriorityRepository repository, RequestRepository requests, PriorityPolicy policy,
                           AuditLogger audit) {
        this.repository = repository;
        this.requests = requests;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional
    public void calculate(long requestId, long actorId) {
        PriorityRepository.Loaded loaded = load(requestId);
        if (loaded.overridden()) {
            throw ApiException.conflict("PRIORITY_OVERRIDDEN",
                    "Request " + requestId + " has an officer override; it is not recalculated automatically.");
        }
        PriorityResult result = PriorityCalculator.calculate(policy, loaded.severity(), loaded.population(),
                loaded.createdAt(), loaded.category(), OffsetDateTime.now(ZoneOffset.UTC));
        repository.store(requestId, result.score(), result.category(), String.join("\n", result.reasons()));
        RequestStatus status = RequestStatus.valueOf(loaded.status());
        requests.addHistory(requestId, "PRIORITY_CALCULATED", status, status, actorId,
                "Score " + result.score() + " (" + result.category() + ")");
        audit.log(actorId, "PRIORITY_CALCULATED", "need_requests", requestId,
                "score " + result.score() + ", category " + result.category());
    }

    @Transactional
    public void override(long requestId, Severity category, String reason, long officerId) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.validation("reason", "A reason is required for an override.");
        }
        PriorityRepository.Loaded loaded = load(requestId);
        if (!loaded.hasScore()) {
            PriorityResult result = PriorityCalculator.calculate(policy, loaded.severity(), loaded.population(),
                    loaded.createdAt(), loaded.category(), OffsetDateTime.now(ZoneOffset.UTC));
            repository.store(requestId, result.score(), result.category(), String.join("\n", result.reasons()));
            loaded = load(requestId);
        }
        String reasons = (loaded.reasons() == null ? "" : loaded.reasons() + "\n")
                + "Overridden by officer to " + category + ": " + trimmed;
        repository.override(requestId, category.name(), trimmed, reasons);
        RequestStatus status = RequestStatus.valueOf(loaded.status());
        requests.addHistory(requestId, "PRIORITY_OVERRIDDEN", status, status, officerId,
                "Category set to " + category + ": " + trimmed);
        audit.log(officerId, "PRIORITY_OVERRIDDEN", "need_requests", requestId, category + ": " + trimmed);
    }

    private PriorityRepository.Loaded load(long requestId) {
        PriorityRepository.Loaded loaded = repository.loadForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("Request " + requestId + " was not found."));
        RequestStatus status = RequestStatus.valueOf(loaded.status());
        if (status != RequestStatus.VERIFIED && status != RequestStatus.APPROVED) {
            throw ApiException.invalidState("Request " + requestId + " is " + status
                    + "; priority applies only to VERIFIED or APPROVED requests.");
        }
        return loaded;
    }
}
