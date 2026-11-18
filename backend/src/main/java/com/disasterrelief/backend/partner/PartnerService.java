package com.disasterrelief.backend.partner;

import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.FieldProblem;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.partner.PartnerDtos.ContributionResponse;
import com.disasterrelief.backend.partner.PartnerDtos.OfferRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partner offers (ASSUMPTION P1, needs approval): an NGO offers on behalf of its own organization; ADMIN or OFFICER
 * accepts/declines and marks accepted offers received. Marking RECEIVED does not add stock: the warehouse manager
 * still records the physical goods through POST /api/inventory/receipts.
 */
@Service
public class PartnerService {

    private final PartnerRepository repository;
    private final AuditLogger audit;

    public PartnerService(PartnerRepository repository, AuditLogger audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public ContributionResponse offer(OfferRequest body, long userId, String role) {
        Long org = "ADMIN".equals(role) ? body.organizationId() : repository.organizationOfUser(userId).orElse(null);
        List<FieldProblem> errors = new ArrayList<>();
        if (org == null) {
            errors.add(new FieldProblem("organizationId", "ADMIN".equals(role)
                    ? "organizationId is required for an administrator."
                    : "Your account is not linked to an organization."));
        } else if (!repository.organizationExists(org)) {
            errors.add(new FieldProblem("organizationId", "Organization not found."));
        }
        if (!repository.resourceActive(body.resourceId())) {
            errors.add(new FieldProblem("resourceId", "Resource not found or no longer active."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        long id = repository.insert(org, body.resourceId(), body.quantity(), body.description(), userId);
        audit.log(userId, "CONTRIBUTION_OFFERED", "partner_contributions", id, body.quantity() + " units");
        return get(id);
    }

    @Transactional(readOnly = true)
    public ContributionResponse get(long id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Contribution " + id + " was not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<ContributionResponse> list(String status, Integer page, Integer size, String sort,
                                                   long userId, String role) {
        Long orgFilter = "NGO".equals(role) ? repository.organizationOfUser(userId).orElse(-1L) : null;
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        PageQuery query = PageQuery.parse(page, size, sort, PartnerRepository.SORTS, "createdAt", true);
        return repository.search(orgFilter, s, query);
    }

    @Transactional
    public ContributionResponse move(long id, String status, long userId) {
        ContributionResponse current = get(id);
        String to = status == null ? "" : status.trim().toUpperCase();
        if (!PartnerRules.canMove(current.status(), to)) {
            throw ApiException.invalidState("Contribution " + id + " is " + current.status() + "; it cannot move to " + to + ".");
        }
        if (!repository.move(id, current.status(), to, userId)) {
            throw ApiException.invalidState("Contribution " + id + " was changed by someone else. Reload it.");
        }
        audit.log(userId, "CONTRIBUTION_" + to, "partner_contributions", id, null);
        return get(id);
    }
}
