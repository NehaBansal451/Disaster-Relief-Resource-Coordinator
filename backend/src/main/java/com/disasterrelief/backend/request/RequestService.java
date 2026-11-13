package com.disasterrelief.backend.request;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.FieldProblem;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Request workflow from docs/API_CONTRACT.md section 1. Every change writes a request_history row in the same transaction. */
@Service
public class RequestService {

    private final RequestRepository repository;

    public RequestService(RequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> list(RequestStatus status, Severity severity, Long disasterId, Long areaId,
                                              Integer page, Integer size, String sort) {
        PageQuery query = PageQuery.parse(page, size, sort, RequestRepository.SORTS, "createdAt", true);
        return repository.search(status, severity, disasterId, areaId, query);
    }

    @Transactional(readOnly = true)
    public RequestResponse get(long id) {
        RequestResponse found = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Request " + id + " was not found."));
        return found.withHistory(repository.history(id));
    }

    @Transactional
    public RequestResponse submit(NeedRequestCreate body, long userId) {
        List<FieldProblem> errors = new ArrayList<>();
        if (!repository.disasterExists(body.disasterId())) {
            errors.add(new FieldProblem("disasterId", "Disaster not found."));
        } else if (!repository.areaBelongsToDisaster(body.affectedAreaId(), body.disasterId())) {
            errors.add(new FieldProblem("affectedAreaId", "This area does not belong to the selected disaster."));
        }
        if (!repository.activeResourceExists(body.resourceId())) {
            errors.add(new FieldProblem("resourceId", "Resource not found or no longer active."));
        }
        if (body.description() != null && body.description().isBlank()) {
            errors.add(new FieldProblem("description", "Description must not be blank."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        long id = repository.insert(body, userId);
        repository.addHistory(id, "CREATED", null, RequestStatus.PENDING, userId, null);
        return get(id);
    }

    @Transactional
    public RequestResponse verify(long id, VerifyRequest body, long officerId) {
        switch (body.decision()) {
            case VERIFY -> move(id, RequestStatus.PENDING, RequestStatus.VERIFIED, "VERIFIED", officerId, null, null);
            case REJECT -> move(id, RequestStatus.PENDING, RequestStatus.REJECTED, "REJECTED", officerId,
                    RequestRules.requireReason(body.reason()), null);
            case REQUEST_CLARIFICATION -> {
                String reason = RequestRules.requireReason(body.reason());
                RequestStatus current = currentOrNotFound(id);
                if (current != RequestStatus.PENDING) {
                    throw notAllowed(id, current, "asked for clarification");
                }
                repository.addHistory(id, "CLARIFICATION_REQUESTED", current, current, officerId, reason);
            }
        }
        return get(id);
    }

    @Transactional
    public RequestResponse approve(long id, NoteRequest body, long officerId) {
        move(id, RequestStatus.VERIFIED, RequestStatus.APPROVED, "APPROVED", officerId,
                RequestRules.optionalNote(body == null ? null : body.note()), officerId);
        return get(id);
    }

    @Transactional
    public RequestResponse reject(long id, NoteRequest body, long officerId) {
        move(id, RequestStatus.VERIFIED, RequestStatus.REJECTED, "REJECTED", officerId,
                RequestRules.requireReason(body == null ? null : body.reason()), null);
        return get(id);
    }

    @Transactional
    public RequestResponse cancel(long id, NoteRequest body, long userId, boolean officer) {
        String reason = RequestRules.requireReason(body == null ? null : body.reason());
        RecordOwner owner = ownerOf(id);
        if (!RequestRules.mayCancel(officer, owner.requesterId() == userId, owner.status())) {
            throw new AccessDeniedException("You may not cancel this request.");
        }
        if (!RequestRules.cancellable(owner.status())) {
            throw notAllowed(id, owner.status(), "cancelled");
        }
        move(id, owner.status(), RequestStatus.CANCELLED, "CANCELLED", userId, reason, null);
        return get(id);
    }

    private record RecordOwner(long requesterId, RequestStatus status) {
    }

    private RecordOwner ownerOf(long id) {
        RequestResponse r = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Request " + id + " was not found."));
        return new RecordOwner(r.requestedBy(), r.status());
    }

    private RequestStatus currentOrNotFound(long id) {
        return repository.statusOf(id).orElseThrow(() -> ApiException.notFound("Request " + id + " was not found."));
    }

    private void move(long id, RequestStatus from, RequestStatus to, String action, long actor, String reason,
                      Long approvedBy) {
        if (!repository.transition(id, from, to, reason, approvedBy)) {
            RequestStatus current = currentOrNotFound(id);
            throw notAllowed(id, current, "moved to " + to);
        }
        repository.addHistory(id, action, from, to, actor, reason);
    }

    private static ApiException notAllowed(long id, RequestStatus current, String what) {
        return ApiException.invalidState("Request " + id + " is " + current + " and cannot be " + what + ".");
    }
}
