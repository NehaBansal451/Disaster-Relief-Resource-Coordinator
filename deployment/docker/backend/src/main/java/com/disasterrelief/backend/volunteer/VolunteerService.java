package com.disasterrelief.backend.volunteer;

import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Coordinates;
import com.disasterrelief.backend.common.FieldProblem;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignmentResponse;
import com.disasterrelief.backend.volunteer.VolunteerDtos.CreateRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.VolunteerResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VolunteerService {

    private final VolunteerRepository repository;
    private final AuditLogger audit;

    public VolunteerService(VolunteerRepository repository, AuditLogger audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public VolunteerResponse create(CreateRequest body, long actorId) {
        List<FieldProblem> errors = new ArrayList<>(Coordinates.check(body.latitude(), body.longitude(), false));
        if (body.userId() != null && !repository.userExists(body.userId())) {
            errors.add(new FieldProblem("userId", "User not found."));
        }
        if (body.organizationId() != null && !repository.organizationExists(body.organizationId())) {
            errors.add(new FieldProblem("organizationId", "Organization not found."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (body.userId() != null && repository.userAlreadyVolunteer(body.userId())) {
            throw ApiException.conflict("VOLUNTEER_EXISTS", "This user already has a volunteer record.");
        }
        long id = repository.insert(body, actorId);
        audit.log(actorId, "VOLUNTEER_REGISTERED", "volunteers", id, null);
        return get(id);
    }

    @Transactional(readOnly = true)
    public VolunteerResponse get(long id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Volunteer " + id + " was not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<VolunteerResponse> list(String availability, String district, Integer page, Integer size, String sort) {
        String a = availability == null || availability.isBlank() ? null : VolunteerRules.normalizeAvailability(availability);
        if (availability != null && !availability.isBlank() && a == null) {
            throw ApiException.validation("availability", "availability must be AVAILABLE, BUSY or OFF_DUTY");
        }
        PageQuery query = PageQuery.parse(page, size, sort, VolunteerRepository.SORTS, "name", false);
        return repository.search(a, district == null || district.isBlank() ? null : district.trim(), query);
    }

    /** Staff may change anyone's availability; a volunteer only their own record. */
    @Transactional
    public VolunteerResponse setAvailability(long id, String value, long actorId, String actorRole) {
        VolunteerResponse volunteer = get(id);
        requireStaffOrSelf(id, actorId, actorRole);
        String availability = VolunteerRules.normalizeAvailability(value);
        if (availability == null) {
            throw ApiException.validation("availability", "availability must be AVAILABLE, BUSY or OFF_DUTY");
        }
        repository.setAvailability(volunteer.id(), availability);
        audit.log(actorId, "VOLUNTEER_AVAILABILITY", "volunteers", id, availability);
        return get(id);
    }

    @Transactional
    public AssignmentResponse assign(long volunteerId, AssignRequest body, long actorId) {
        get(volunteerId);
        List<FieldProblem> errors = new ArrayList<>();
        if (!repository.disasterExists(body.disasterId())) {
            errors.add(new FieldProblem("disasterId", "Disaster not found."));
        }
        if (body.requestId() != null && !repository.requestExists(body.requestId())) {
            errors.add(new FieldProblem("requestId", "Request not found."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (!repository.markBusyIfAvailable(volunteerId)) {
            throw ApiException.conflict("VOLUNTEER_UNAVAILABLE", "Volunteer " + volunteerId + " is not AVAILABLE.");
        }
        long id = repository.insertAssignment(volunteerId, body.disasterId(), body.requestId(), body.task(), actorId);
        audit.log(actorId, "VOLUNTEER_ASSIGNED", "volunteer_assignments", id, "volunteer " + volunteerId);
        return repository.findAssignment(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(long volunteerId, long actorId, String actorRole) {
        get(volunteerId);
        requireStaffOrSelf(volunteerId, actorId, actorRole);
        return repository.assignmentsOf(volunteerId);
    }

    @Transactional
    public AssignmentResponse moveAssignment(long assignmentId, String status, long actorId, String actorRole) {
        AssignmentResponse current = repository.findAssignment(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment " + assignmentId + " was not found."));
        requireStaffOrSelf(current.volunteerId(), actorId, actorRole);
        String to = status == null ? "" : status.trim().toUpperCase();
        if (!VolunteerRules.canMove(current.status(), to)) {
            throw ApiException.invalidState("Assignment " + assignmentId + " is " + current.status() + "; it cannot move to " + to + ".");
        }
        if (!repository.moveAssignment(assignmentId, current.status(), to)) {
            throw ApiException.invalidState("Assignment " + assignmentId + " was changed by someone else. Reload it.");
        }
        if (VolunteerRules.freesVolunteer(to)) {
            repository.setAvailability(current.volunteerId(), "AVAILABLE");
        }
        audit.log(actorId, "ASSIGNMENT_" + to, "volunteer_assignments", assignmentId, null);
        return repository.findAssignment(assignmentId).orElseThrow();
    }

    private void requireStaffOrSelf(long volunteerId, long actorId, String actorRole) {
        if (VolunteerRules.isStaff(actorRole)) {
            return;
        }
        Long owner = repository.userIdOf(volunteerId).orElse(null);
        if (owner == null || owner != actorId) {
            throw new AccessDeniedException("You can only manage your own volunteer record.");
        }
    }
}
