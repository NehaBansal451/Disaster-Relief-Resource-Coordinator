package com.disasterrelief.backend.shelter;

import com.disasterrelief.backend.alert.AlertService;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShelterService {

    private final ShelterRepository repository;
    private final AlertService alerts;

    public ShelterService(ShelterRepository repository, AlertService alerts) {
        this.repository = repository;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public PageResponse<ShelterResponse> list(String status, Boolean overcrowdedOnly,
                                              Integer page, Integer size, String sort) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalized != null && !ShelterRules.isStatus(normalized)) {
            throw ApiException.validation("status", "status must be OPEN, CLOSED or INACTIVE");
        }
        PageQuery query = PageQuery.parse(page, size, sort, ShelterRepository.SORTS, "name", false);
        return repository.search(normalized, Boolean.TRUE.equals(overcrowdedOnly), query);
    }

    @Transactional(readOnly = true)
    public ShelterResponse get(long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Shelter " + id + " was not found."));
    }

    @Transactional(readOnly = true)
    public List<ShelterResponse> alternatives(long id) {
        get(id);
        return repository.alternatives(id, 5);
    }

    @Transactional
    public ShelterResponse create(ShelterRequest request, long userId) {
        ShelterRules.validate(request);
        return get(repository.insert(request));
    }

    @Transactional
    public ShelterResponse updateOccupancy(long id, OccupancyRequest request) {
        if (!repository.updateOccupancy(id, request.occupancy())) {
            throw ApiException.notFound("Shelter " + id + " was not found.");
        }
        ShelterResponse updated = get(id);
        if (updated.overcrowded()) {
            alerts.raise("OVERCROWDED_SHELTER", "HIGH", updated.name() + " is overcrowded: " + updated.occupancy()
                    + " people for " + updated.capacity() + " places.", "shelters", id);
        }
        return updated;
    }
}
