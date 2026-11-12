package com.disasterrelief.backend.affectedarea;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.common.Severity;
import com.disasterrelief.backend.disaster.DisasterResponse;
import com.disasterrelief.backend.disaster.DisasterService;
import com.disasterrelief.backend.disaster.DisasterStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AffectedAreaService {

    private final AffectedAreaRepository repository;
    private final DisasterService disasters;

    public AffectedAreaService(AffectedAreaRepository repository, DisasterService disasters) {
        this.repository = repository;
        this.disasters = disasters;
    }

    @Transactional(readOnly = true)
    public PageResponse<AffectedAreaResponse> list(Long disasterId, Severity impactLevel,
                                                   Integer page, Integer size, String sort) {
        PageQuery query = PageQuery.parse(page, size, sort, AffectedAreaRepository.SORTS, "name", false);
        return repository.search(disasterId, impactLevel, query);
    }

    @Transactional
    public AffectedAreaResponse create(AffectedAreaRequest request) {
        AffectedAreaRules.validate(request);

        // 404 when the disaster does not exist. A closed disaster is history: no new areas (ASSUMPTION, needs approval).
        DisasterResponse disaster = disasters.get(request.disasterId());
        if (disaster.status() == DisasterStatus.CLOSED) {
            throw ApiException.invalidState("Disaster " + disaster.id() + " is CLOSED; affected areas cannot be added.");
        }

        try {
            long id = repository.insert(request, AffectedAreaRules.effectiveType(request));
            return repository.findById(id).orElseThrow();
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("DUPLICATE_AREA",
                    "This disaster already has an area with the same name and district.");
        }
    }
}
