package com.disasterrelief.backend.inventory;

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

import com.disasterrelief.backend.common.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class InventoryServiceTest {

    private InventoryRepository repo;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repo = mock(InventoryRepository.class);
        service = new InventoryService(repo);
    }

    private static ReceiptRequest receipt(LocalDate expiry) {
        return new ReceiptRequest(1L, 2L, 100, " lot-1 ", expiry, " note ");
    }

    private static InventoryRow row() {
        return new InventoryRow(9, 1, "PTL-01", "Depot", 2, "Water", "litre", "LOT-1", 100, 0, 100, null, "NONE");
    }

    @Test
    void managerOfAnotherWarehouseIsRefused() {
        when(repo.warehouse(1)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, 55L)));
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.of(false));
        assertThrows(AccessDeniedException.class, () -> service.receive(receipt(null), 7, false));
        verify(repo, never()).addStock(anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void managerOfTheWarehouseCanReceiveAndLedgerRowIsWritten() {
        when(repo.warehouse(1)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, 7L)));
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.of(false));
        when(repo.addStock(1, 2, "LOT-1", 100, null)).thenReturn(Optional.of(new InventoryRepository.Receipt(9, 100, 0)));
        when(repo.findById(9)).thenReturn(Optional.of(row()));
        assertEquals(100, service.receive(receipt(null), 7, false).available());
        verify(repo).addLedger(9, "RECEIPT", 100, 0, null, "note", null, 7);
    }

    @Test
    void administratorMayReceiveAnywhere() {
        when(repo.warehouse(1)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, 55L)));
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.of(false));
        when(repo.addStock(eq(1L), eq(2L), any(), eq(100), any())).thenReturn(Optional.of(new InventoryRepository.Receipt(9, 100, 0)));
        when(repo.findById(9)).thenReturn(Optional.of(row()));
        service.receive(receipt(null), 1, true);
    }

    @Test
    void unknownOrInactiveWarehouseAndResourceAreValidationErrors() {
        when(repo.warehouse(1)).thenReturn(Optional.empty());
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.receive(receipt(null), 1, true));
        assertEquals(2, ex.errors().size());
        when(repo.warehouse(1)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(false, 1L)));
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.of(false));
        assertEquals("warehouseId", assertThrows(ApiException.class, () -> service.receive(receipt(null), 1, true)).errors().get(0).field());
    }

    @Test
    void perishableWithoutExpiryIsRefused() {
        when(repo.warehouse(1)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, 7L)));
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.of(true));
        assertThrows(ApiException.class, () -> service.receive(receipt(null), 7, false));
        verify(repo, never()).addStock(anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void differentExpiryForExistingBatchIsAConflict() {
        when(repo.warehouse(1)).thenReturn(Optional.of(new InventoryRepository.WarehouseInfo(true, 7L)));
        when(repo.activeResourcePerishable(2)).thenReturn(Optional.of(false));
        when(repo.addStock(eq(1L), eq(2L), any(), eq(100), any())).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class,
                () -> service.receive(receipt(LocalDate.now().plusDays(60)), 7, false));
        assertEquals("BATCH_EXPIRY_MISMATCH", ex.code());
        verify(repo, never()).addLedger(anyLong(), any(), anyInt(), anyInt(), any(), any(), any(), anyLong());
    }

    @Test
    void ledgerOfUnknownLineIsNotFound() {
        when(repo.exists(5)).thenReturn(false);
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.ledger(5)).code());
    }
}
