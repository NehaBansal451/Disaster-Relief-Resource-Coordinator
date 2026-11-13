package com.disasterrelief.backend.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Severity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class RequestServiceTest {

    private RequestRepository repo;
    private RequestService service;

    @BeforeEach
    void setUp() {
        repo = mock(RequestRepository.class);
        service = new RequestService(repo);
    }

    private static RequestResponse sample(long id, RequestStatus status, long requester) {
        return new RequestResponse(id, 1, "D", 2, "A", 3, "Water", "litre", requester, "R", 10, 5, Severity.HIGH,
                "d", status, null, null, null, List.of(), null, null, null, List.of());
    }

    private static NeedRequestCreate create() {
        return new NeedRequestCreate(1L, 2L, 3L, 10, 5, Severity.HIGH, "need water");
    }

    @Test
    void submitRejectsAreaFromAnotherDisaster() {
        when(repo.disasterExists(1)).thenReturn(true);
        when(repo.areaBelongsToDisaster(2, 1)).thenReturn(false);
        when(repo.activeResourceExists(3)).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.submit(create(), 7));
        assertEquals("affectedAreaId", ex.errors().get(0).field());
        verify(repo, never()).insert(any(), anyLong());
    }

    @Test
    void submitStoresRequestAndCreatedHistory() {
        when(repo.disasterExists(1)).thenReturn(true);
        when(repo.areaBelongsToDisaster(2, 1)).thenReturn(true);
        when(repo.activeResourceExists(3)).thenReturn(true);
        when(repo.insert(any(), eq(7L))).thenReturn(50L);
        when(repo.findById(50)).thenReturn(Optional.of(sample(50, RequestStatus.PENDING, 7)));
        when(repo.history(50)).thenReturn(List.of());
        assertEquals(RequestStatus.PENDING, service.submit(create(), 7).status());
        verify(repo).addHistory(50, "CREATED", null, RequestStatus.PENDING, 7, null);
    }

    @Test
    void verifyMovesPendingToVerified() {
        when(repo.transition(5, RequestStatus.PENDING, RequestStatus.VERIFIED, null, null)).thenReturn(true);
        when(repo.findById(5)).thenReturn(Optional.of(sample(5, RequestStatus.VERIFIED, 7)));
        service.verify(5, new VerifyRequest(VerifyDecision.VERIFY, null), 1);
        verify(repo).addHistory(5, "VERIFIED", RequestStatus.PENDING, RequestStatus.VERIFIED, 1, null);
    }

    @Test
    void rejectNeedsReason() {
        assertThrows(ApiException.class, () -> service.verify(5, new VerifyRequest(VerifyDecision.REJECT, " "), 1));
        assertThrows(ApiException.class, () -> service.reject(5, new NoteRequest(null, null), 1));
        verify(repo, never()).transition(anyLong(), any(), any(), any(), any());
    }

    @Test
    void secondOfficerGetsConflictWhenRequestAlreadyMoved() {
        when(repo.transition(5, RequestStatus.VERIFIED, RequestStatus.APPROVED, null, 1L)).thenReturn(false);
        when(repo.statusOf(5)).thenReturn(Optional.of(RequestStatus.REJECTED));
        ApiException ex = assertThrows(ApiException.class, () -> service.approve(5, null, 1));
        assertEquals("INVALID_STATE", ex.code());
        verify(repo, never()).addHistory(anyLong(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void unknownRequestIsNotFound() {
        when(repo.transition(anyLong(), any(), any(), any(), any())).thenReturn(false);
        when(repo.statusOf(9)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.reject(9, new NoteRequest(null, "x"), 1)).code());
    }

    @Test
    void clarificationOnlyWhilePendingAndKeepsStatus() {
        when(repo.statusOf(5)).thenReturn(Optional.of(RequestStatus.VERIFIED));
        assertThrows(ApiException.class,
                () -> service.verify(5, new VerifyRequest(VerifyDecision.REQUEST_CLARIFICATION, "which village?"), 1));
        when(repo.statusOf(6)).thenReturn(Optional.of(RequestStatus.PENDING));
        when(repo.findById(6)).thenReturn(Optional.of(sample(6, RequestStatus.PENDING, 7)));
        service.verify(6, new VerifyRequest(VerifyDecision.REQUEST_CLARIFICATION, "which village?"), 1);
        verify(repo, never()).transition(eq(6L), any(), any(), any(), any());
        verify(repo).addHistory(6, "CLARIFICATION_REQUESTED", RequestStatus.PENDING, RequestStatus.PENDING, 1, "which village?");
    }

    @Test
    void requesterCannotCancelAfterVerification() {
        when(repo.findById(5)).thenReturn(Optional.of(sample(5, RequestStatus.VERIFIED, 7)));
        assertThrows(AccessDeniedException.class, () -> service.cancel(5, new NoteRequest(null, "oops"), 7, false));
    }

    @Test
    void requesterCanCancelOwnPendingAndOfficerCanCancelApproved() {
        when(repo.findById(5)).thenReturn(Optional.of(sample(5, RequestStatus.PENDING, 7)));
        when(repo.transition(5, RequestStatus.PENDING, RequestStatus.CANCELLED, "oops", null)).thenReturn(true);
        service.cancel(5, new NoteRequest(null, "oops"), 7, false);
        when(repo.findById(6)).thenReturn(Optional.of(sample(6, RequestStatus.APPROVED, 7)));
        when(repo.transition(6, RequestStatus.APPROVED, RequestStatus.CANCELLED, "stock", null)).thenReturn(true);
        service.cancel(6, new NoteRequest(null, "stock"), 1, true);
    }

    @Test
    void allocatedRequestCannotBeCancelledYet() {
        when(repo.findById(5)).thenReturn(Optional.of(sample(5, RequestStatus.ALLOCATED, 7)));
        assertEquals("INVALID_STATE",
                assertThrows(ApiException.class, () -> service.cancel(5, new NoteRequest(null, "x"), 1, true)).code());
    }
}
