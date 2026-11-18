package com.disasterrelief.backend.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.alert.AlertService;
import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.dispatch.DispatchDtos.CreateRequest;
import com.disasterrelief.backend.dispatch.DispatchDtos.DispatchResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.StatusRequest;
import com.disasterrelief.backend.inventory.InventoryRepository;
import com.disasterrelief.backend.request.RequestRepository;
import com.disasterrelief.backend.request.RequestStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DispatchServiceTest {

    private DispatchRepository repo;
    private RequestRepository requests;
    private InventoryRepository inventory;
    private DispatchService service;

    @BeforeEach
    void setUp() {
        repo = mock(DispatchRepository.class);
        requests = mock(RequestRepository.class);
        inventory = mock(InventoryRepository.class);
        service = new DispatchService(repo, requests, inventory, mock(AuditLogger.class), mock(AlertService.class));
    }

    private static DispatchResponse response(String status) {
        return new DispatchResponse(50, 1, 10, 5, "W5", "Blanket", "piece", 100, status, null, null, null, null, null, List.of());
    }

    private static CreateRequest create() {
        return new CreateRequest(1L, 5L, " truck ", " Ram ", null);
    }

    private void approvedAllocationAtWarehouseManagedBy(long managerId) {
        when(repo.allocationFacts(1)).thenReturn(Optional.of(new DispatchRepository.AllocationFacts("APPROVED", 10, 3)));
        when(repo.warehouseInAllocation(1, 5)).thenReturn(true);
        when(inventory.warehouse(5)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, managerId)));
    }

    @Test
    void createDeductsReservedStockWritesLedgerAndMovesRequestToDispatched() {
        approvedAllocationAtWarehouseManagedBy(9);
        when(repo.lockReservations(1, 5)).thenReturn(List.of(new DispatchRepository.Reservation(1, 77, 100, 3)));
        when(repo.deduct(77, 100)).thenReturn(Optional.of(new DispatchRepository.Balance(400, 0)));
        when(repo.insert(1, 10, 5, 100, "truck", "Ram", null, 9)).thenReturn(50L);
        when(repo.find(50)).thenReturn(Optional.of(response("DISPATCHED")));
        when(requests.transition(10, RequestStatus.ALLOCATED, RequestStatus.DISPATCHED, null, null)).thenReturn(true);

        assertEquals("DISPATCHED", service.create(create(), 9, false).status());

        verify(inventory).addLedger(77, "DISPATCH", -100, -100, 10L, "Dispatch for allocation 1", null, 9);
        verify(repo).markDispatched(1);
        verify(repo).addUpdate(50, "DISPATCHED", null, 9);
    }

    @Test
    void managerOfAnotherWarehouseIsRefusedButAdminIsNot() {
        approvedAllocationAtWarehouseManagedBy(55);
        assertThrows(AccessDeniedException.class, () -> service.create(create(), 9, false));
        verify(repo, never()).lockReservations(anyLong(), anyLong());
    }

    @Test
    void allocationMustBeApprovedAndContainTheWarehouse() {
        when(repo.allocationFacts(1)).thenReturn(Optional.of(new DispatchRepository.AllocationFacts("RECOMMENDED", 10, 3)));
        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.create(create(), 9, true)).code());
        when(repo.allocationFacts(1)).thenReturn(Optional.of(new DispatchRepository.AllocationFacts("APPROVED", 10, 3)));
        when(repo.warehouseInAllocation(1, 5)).thenReturn(false);
        assertEquals("warehouseId", assertThrows(ApiException.class, () -> service.create(create(), 9, true)).errors().get(0).field());
    }

    @Test
    void doubleDispatchIsRefused() {
        approvedAllocationAtWarehouseManagedBy(9);
        when(repo.exists(1, 5)).thenReturn(true);
        assertEquals("ALREADY_DISPATCHED", assertThrows(ApiException.class, () -> service.create(create(), 9, false)).code());
    }

    @Test
    void nothingReservedMeansNothingToDispatch() {
        approvedAllocationAtWarehouseManagedBy(9);
        when(repo.lockReservations(1, 5)).thenReturn(List.of());
        assertEquals("NOTHING_TO_DISPATCH", assertThrows(ApiException.class, () -> service.create(create(), 9, false)).code());
    }

    @Test
    void cannotSkipInTransit() {
        when(repo.lock(50)).thenReturn(Optional.of(new DispatchRepository.Locked(50, 1, 10, 5, "DISPATCHED")));
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.updateStatus(50, new StatusRequest("DELIVERED", null), 9, "OFFICER")).code());
        verify(repo, never()).setStatus(anyLong(), any());
    }

    @Test
    void inTransitMovesTheRequestToo() {
        when(repo.lock(50)).thenReturn(Optional.of(new DispatchRepository.Locked(50, 1, 10, 5, "DISPATCHED")));
        when(repo.find(50)).thenReturn(Optional.of(response("IN_TRANSIT")));
        when(requests.transition(10, RequestStatus.DISPATCHED, RequestStatus.IN_TRANSIT, null, null)).thenReturn(true);
        service.updateStatus(50, new StatusRequest("IN_TRANSIT", null), 9, "FIELD_WORKER");
        verify(requests).addHistory(10, "IN_TRANSIT", RequestStatus.DISPATCHED, RequestStatus.IN_TRANSIT, 9, "Dispatch 50 is on the way");
    }

    @Test
    void requestIsDeliveredOnlyWhenEveryDispatchOfTheAllocationIsDelivered() {
        when(repo.lock(50)).thenReturn(Optional.of(new DispatchRepository.Locked(50, 1, 10, 5, "IN_TRANSIT")));
        when(repo.find(50)).thenReturn(Optional.of(response("DELIVERED")));
        when(repo.itemCount(1)).thenReturn(2);
        when(repo.deliveredCount(1)).thenReturn(1);
        service.updateStatus(50, new StatusRequest("DELIVERED", null), 9, "OFFICER");
        verify(requests, never()).transition(anyLong(), any(), any(), any(), any());

        when(repo.deliveredCount(1)).thenReturn(2);
        when(requests.transition(10, RequestStatus.IN_TRANSIT, RequestStatus.DELIVERED, null, null)).thenReturn(true);
        when(repo.lock(50)).thenReturn(Optional.of(new DispatchRepository.Locked(50, 1, 10, 5, "IN_TRANSIT")));
        service.updateStatus(50, new StatusRequest("DELIVERED", null), 9, "OFFICER");
        verify(requests).transition(10, RequestStatus.IN_TRANSIT, RequestStatus.DELIVERED, null, null);
    }

    @Test
    void warehouseManagerMayOnlyUpdateOwnWarehouseDispatches() {
        when(repo.lock(50)).thenReturn(Optional.of(new DispatchRepository.Locked(50, 1, 10, 5, "DISPATCHED")));
        when(inventory.warehouse(5)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, 55L)));
        assertThrows(AccessDeniedException.class,
                () -> service.updateStatus(50, new StatusRequest("IN_TRANSIT", null), 9, "WAREHOUSE_MANAGER"));
    }
}
