package com.disasterrelief.backend.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.alert.AlertService;
import com.disasterrelief.backend.allocation.AllocationDtos.ApproveRequest;
import com.disasterrelief.backend.allocation.AllocationDtos.ItemEdit;
import com.disasterrelief.backend.allocation.AllocationDtos.ItemResponse;
import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.inventory.InventoryRepository;
import com.disasterrelief.backend.request.RequestRepository;
import com.disasterrelief.backend.request.RequestStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllocationServiceTest {

    private AllocationRepository repo;
    private RequestRepository requests;
    private InventoryRepository inventory;
    private AllocationService service;

    @BeforeEach
    void setUp() {
        repo = mock(AllocationRepository.class);
        requests = mock(RequestRepository.class);
        inventory = mock(InventoryRepository.class);
        service = new AllocationService(repo, requests, inventory, mock(AuditLogger.class), mock(AlertService.class));
    }

    private static AllocationRepository.Row row(String status, int version) {
        return new AllocationRepository.Row(1, 10, status, 100, 100, version, "expl", null, null, null);
    }

    private void approvedRequestWithOneItem() {
        when(repo.requestFacts(10)).thenReturn(Optional.of(new AllocationRepository.RequestFacts("APPROVED", 3, 4, 100)));
        when(repo.items(1)).thenReturn(List.of(new ItemResponse(5, "W5", 100, 3.0, "reason")));
        when(repo.activeWarehouseIds(any())).thenReturn(List.of(5L));
    }

    @Test
    void approveReservesStockWritesLedgerAndMovesRequestToAllocated() {
        when(repo.lock(1)).thenReturn(Optional.of(row("RECOMMENDED", 1)));
        when(repo.find(1)).thenReturn(Optional.of(row("APPROVED", 2)));
        approvedRequestWithOneItem();
        when(repo.lockLines(3, List.of(5L))).thenReturn(List.of(new AllocationRepository.Line(77, 5, 500, 0, null)));
        when(repo.reserve(77, 100)).thenReturn(Optional.of(new AllocationRepository.Balance(500, 100)));
        when(requests.transition(10, RequestStatus.APPROVED, RequestStatus.ALLOCATED, null, null)).thenReturn(true);

        service.approve(1, new ApproveRequest(1, null, "ok"), 9);

        verify(inventory).addLedger(77, "RESERVATION", 0, 100, 10L, "Allocation 1", null, 9);
        verify(repo).insertReservation(1, 77, 100);
        verify(repo).markApproved(1, 9, "ok", 100);
        verify(requests).addHistory(eq(10L), eq("ALLOCATED"), eq(RequestStatus.APPROVED), eq(RequestStatus.ALLOCATED), eq(9L), any());
    }

    @Test
    void staleAllocationAndWrongVersionAreConflicts() {
        when(repo.lock(1)).thenReturn(Optional.of(row("STALE", 1)));
        assertEquals("STALE_RECOMMENDATION",
                assertThrows(ApiException.class, () -> service.approve(1, new ApproveRequest(1, null, null), 9)).code());
        when(repo.lock(2)).thenReturn(Optional.of(row("RECOMMENDED", 3)));
        assertEquals("STALE_VERSION",
                assertThrows(ApiException.class, () -> service.approve(2, new ApproveRequest(1, null, null), 9)).code());
        verify(repo, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void alreadyApprovedCannotBeApprovedTwice() {
        when(repo.lock(1)).thenReturn(Optional.of(row("APPROVED", 2)));
        assertEquals("INVALID_STATE",
                assertThrows(ApiException.class, () -> service.approve(1, new ApproveRequest(2, null, null), 9)).code());
    }

    @Test
    void notEnoughStockAtApprovalTimeIsRefusedNotPartiallyReserved() {
        when(repo.lock(1)).thenReturn(Optional.of(row("RECOMMENDED", 1)));
        approvedRequestWithOneItem();
        when(repo.lockLines(3, List.of(5L))).thenReturn(List.of(new AllocationRepository.Line(77, 5, 500, 490, null)));
        when(repo.reserve(77, 10)).thenReturn(Optional.of(new AllocationRepository.Balance(500, 500)));
        ApiException ex = assertThrows(ApiException.class, () -> service.approve(1, new ApproveRequest(1, null, null), 9));
        assertEquals("INSUFFICIENT_STOCK", ex.code());
        verify(repo, never()).markApproved(anyLong(), anyLong(), any(), anyInt());
        verify(requests, never()).transition(anyLong(), any(), any(), any(), any());
    }

    @Test
    void databaseGuardFailureIsReportedAsInsufficientStock() {
        when(repo.lock(1)).thenReturn(Optional.of(row("RECOMMENDED", 1)));
        approvedRequestWithOneItem();
        when(repo.lockLines(3, List.of(5L))).thenReturn(List.of(new AllocationRepository.Line(77, 5, 500, 0, null)));
        when(repo.reserve(77, 100)).thenReturn(Optional.empty());
        assertEquals("INSUFFICIENT_STOCK",
                assertThrows(ApiException.class, () -> service.approve(1, new ApproveRequest(1, null, null), 9)).code());
    }

    @Test
    void requestThatIsNoLongerApprovedCannotBeAllocated() {
        when(repo.lock(1)).thenReturn(Optional.of(row("RECOMMENDED", 1)));
        when(repo.requestFacts(10)).thenReturn(Optional.of(new AllocationRepository.RequestFacts("CANCELLED", 3, 4, 100)));
        assertEquals("INVALID_STATE",
                assertThrows(ApiException.class, () -> service.approve(1, new ApproveRequest(1, null, null), 9)).code());
    }

    @Test
    void editedPlanMustNotExceedTheRequestedQuantityOrRepeatAWarehouse() {
        when(repo.lock(1)).thenReturn(Optional.of(row("RECOMMENDED", 1)));
        when(repo.requestFacts(10)).thenReturn(Optional.of(new AllocationRepository.RequestFacts("APPROVED", 3, 4, 100)));
        ApiException tooMuch = assertThrows(ApiException.class, () -> service.approve(1,
                new ApproveRequest(1, List.of(new ItemEdit(5L, 80), new ItemEdit(6L, 80)), null), 9));
        assertEquals("VALIDATION_ERROR", tooMuch.code());
        ApiException dup = assertThrows(ApiException.class, () -> service.approve(1,
                new ApproveRequest(1, List.of(new ItemEdit(5L, 10), new ItemEdit(5L, 10)), null), 9));
        assertEquals("items", dup.errors().get(0).field());
        verify(repo, never()).lockLines(anyLong(), any());
    }

    @Test
    void recommendOnlyForApprovedRequests() {
        when(repo.requestFacts(10)).thenReturn(Optional.of(new AllocationRepository.RequestFacts("PENDING", 3, 4, 100)));
        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.recommend(10, 9)).code());
        verify(repo, never()).insertAllocation(anyLong(), anyInt(), anyInt(), any(), anyLong());
    }

    @Test
    void recommendStoresPlanAndMarksOldOnesStale() {
        when(repo.requestFacts(10)).thenReturn(Optional.of(new AllocationRepository.RequestFacts("APPROVED", 3, 4, 100)));
        when(repo.candidates(3, 4)).thenReturn(List.of(new AllocationRules.Candidate(5, "W5", 500, 3.0, null)));
        when(repo.competingDemand(10, 3)).thenReturn(0);
        when(repo.warehousesWithoutStock(List.of(5L))).thenReturn(List.of());
        when(repo.insertAllocation(eq(10L), eq(100), eq(100), any(), eq(9L))).thenReturn(1L);
        when(repo.find(1)).thenReturn(Optional.of(row("RECOMMENDED", 1)));
        service.recommend(10, 9);
        verify(repo).markStale(10);
        verify(repo).insertItem(eq(1L), eq(5L), eq(100), eq(3.0), any());
    }

    @Test
    void recommendIsRefusedWhenAnApprovedAllocationExists() {
        when(repo.requestFacts(10)).thenReturn(Optional.of(new AllocationRepository.RequestFacts("APPROVED", 3, 4, 100)));
        when(repo.hasApprovedAllocation(10)).thenReturn(true);
        assertEquals("ALREADY_ALLOCATED", assertThrows(ApiException.class, () -> service.recommend(10, 9)).code());
    }

    @Test
    void rejectOnlyFromRecommended() {
        when(repo.lock(1)).thenReturn(Optional.of(row("APPROVED", 2)));
        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.reject(1, "no", 9)).code());
    }
}
