package com.disasterrelief.backend.disaster;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisasterService {

    private final DisasterRepository repository;

    public DisasterService(DisasterRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DisasterResponse> list(DisasterStatus status, Severity severity,
                                               Integer page, Integer size, String sort) {
        PageQuery query = PageQuery.parse(page, size, sort, DisasterRepository.SORTS, "startedAt", true);
        return repository.search(status, severity, query);
    }

    @Transactional(readOnly = true)
    public DisasterResponse get(long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Disaster " + id + " was not found."));
    }

    @Transactional
    public DisasterResponse create(DisasterRequest request, long userId) {
        DisasterRules.validate(request);
        long id = repository.insert(request, DisasterRules.effectiveStatus(request), userId);
        return get(id);
    }

    @Transactional
    public DisasterResponse update(long id, DisasterRequest request) {
        DisasterRules.validate(request);
        if (!repository.update(id, request, DisasterRules.effectiveStatus(request))) {
            throw ApiException.notFound("Disaster " + id + " was not found.");
        }
        return get(id);
    }
}
