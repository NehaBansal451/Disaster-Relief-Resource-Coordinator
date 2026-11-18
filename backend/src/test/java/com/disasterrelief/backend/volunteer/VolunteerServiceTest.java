package com.disasterrelief.backend.volunteer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignRequest;
import com.disasterrelief.backend.volunteer.VolunteerDtos.AssignmentResponse;
import com.disasterrelief.backend.volunteer.VolunteerDtos.VolunteerResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class VolunteerServiceTest {

    private VolunteerRepository repo;
    private VolunteerService service;

    @BeforeEach
    void setUp() {
        repo = mock(VolunteerRepository.class);
        service = new VolunteerService(repo, mock(AuditLogger.class));
        when(repo.userIdOf(3)).thenReturn(Optional.of(42L));
        when(repo.find(3)).thenReturn(Optional.of(new VolunteerResponse(3, 42L, null, "Asha", null, null,
                "AVAILABLE", null, null, null, null, null)));
    }

    private static AssignmentResponse assignment(String status) {
        return new AssignmentResponse(8, 3, 1, null, "Distribute food", status, null, null);
    }

    @Test
    void assignMarksVolunteerBusyThenCreatesTheAssignment() {
        when(repo.disasterExists(1)).thenReturn(true);
        when(repo.markBusyIfAvailable(3)).thenReturn(true);
        when(repo.insertAssignment(3, 1, null, "Distribute food", 9)).thenReturn(8L);
        when(repo.findAssignment(8)).thenReturn(Optional.of(assignment("ASSIGNED")));
        assertEquals(8, service.assign(3, new AssignRequest(1L, null, "Distribute food"), 9).id());
    }

    @Test
    void busyVolunteerCannotBeAssigned() {
        when(repo.disasterExists(1)).thenReturn(true);
        when(repo.markBusyIfAvailable(3)).thenReturn(false);
        ApiException ex = assertThrows(ApiException.class, () -> service.assign(3, new AssignRequest(1L, null, "x"), 9));
        assertEquals("VOLUNTEER_UNAVAILABLE", ex.code());
        verify(repo, never()).insertAssignment(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void unknownDisasterOrRequestIsAValidationError() {
        when(repo.disasterExists(1)).thenReturn(false);
        when(repo.requestExists(5)).thenReturn(false);
        ApiException ex = assertThrows(ApiException.class, () -> service.assign(3, new AssignRequest(1L, 5L, "x"), 9));
        assertEquals(2, ex.errors().size());
        verify(repo, never()).markBusyIfAvailable(anyLong());
    }

    @Test
    void completingFreesTheVolunteer() {
        when(repo.findAssignment(8)).thenReturn(Optional.of(assignment("ACCEPTED")));
        when(repo.moveAssignment(8, "ACCEPTED", "COMPLETED")).thenReturn(true);
        service.moveAssignment(8, "completed", 42, "VOLUNTEER");
        verify(repo).setAvailability(3, "AVAILABLE");
    }

    @Test
    void acceptingKeepsTheVolunteerBusy() {
        when(repo.findAssignment(8)).thenReturn(Optional.of(assignment("ASSIGNED")));
        when(repo.moveAssignment(8, "ASSIGNED", "ACCEPTED")).thenReturn(true);
        service.moveAssignment(8, "ACCEPTED", 42, "VOLUNTEER");
        verify(repo, never()).setAvailability(anyLong(), any());
    }

    @Test
    void illegalMoveAndLostRaceAreConflicts() {
        when(repo.findAssignment(8)).thenReturn(Optional.of(assignment("ASSIGNED")));
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.moveAssignment(8, "COMPLETED", 42, "VOLUNTEER")).code());
        when(repo.moveAssignment(8, "ASSIGNED", "ACCEPTED")).thenReturn(false);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.moveAssignment(8, "ACCEPTED", 42, "VOLUNTEER")).code());
    }

    @Test
    void aVolunteerCannotTouchSomeoneElsesAssignmentButStaffCan() {
        when(repo.findAssignment(8)).thenReturn(Optional.of(assignment("ASSIGNED")));
        when(repo.userIdOf(3)).thenReturn(Optional.of(42L));
        assertThrows(AccessDeniedException.class, () -> service.moveAssignment(8, "ACCEPTED", 99, "VOLUNTEER"));
        when(repo.moveAssignment(8, "ASSIGNED", "DECLINED")).thenReturn(true);
        service.moveAssignment(8, "DECLINED", 99, "OFFICER");
    }

    @Test
    void availabilityValueIsValidated() {
        when(repo.userIdOf(3)).thenReturn(Optional.of(42L));
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service.setAvailability(3, "napping", 42, "VOLUNTEER")).code());
        service.setAvailability(3, "off_duty", 42, "VOLUNTEER");
        verify(repo).setAvailability(3, "OFF_DUTY");
    }

    @Test
    void unknownVolunteerIsNotFound() {
        when(repo.find(404)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.get(404)).code());
    }
}
