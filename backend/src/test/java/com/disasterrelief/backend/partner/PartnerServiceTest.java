package com.disasterrelief.backend.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.partner.PartnerDtos.ContributionResponse;
import com.disasterrelief.backend.partner.PartnerDtos.OfferRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartnerServiceTest {

    private PartnerRepository repo;
    private PartnerService service;

    @BeforeEach
    void setUp() {
        repo = mock(PartnerRepository.class);
        service = new PartnerService(repo, mock(AuditLogger.class));
    }

    private static ContributionResponse row(String status) {
        return new ContributionResponse(4, 2, "Org", 6, "Blanket", 100, null, status, null, null);
    }

    @Test
    void ngoOffersOnBehalfOfItsOwnOrganization() {
        when(repo.organizationOfUser(11)).thenReturn(Optional.of(2L));
        when(repo.organizationExists(2)).thenReturn(true);
        when(repo.resourceActive(6)).thenReturn(true);
        when(repo.insert(2, 6, 100, "d", 11)).thenReturn(4L);
        when(repo.find(4)).thenReturn(Optional.of(row("OFFERED")));
        assertEquals(4, service.offer(new OfferRequest(777L, 6L, 100, "d"), 11, "NGO").id());
        verify(repo).insert(2, 6, 100, "d", 11);
    }

    @Test
    void ngoWithoutOrganizationIsRefused() {
        when(repo.organizationOfUser(11)).thenReturn(Optional.empty());
        when(repo.resourceActive(6)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.offer(new OfferRequest(null, 6L, 100, null), 11, "NGO"));
        verify(repo, never()).insert(anyLong(), anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    void adminMustNameTheOrganization() {
        when(repo.resourceActive(6)).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.offer(new OfferRequest(null, 6L, 100, null), 1, "ADMIN"));
        assertEquals("organizationId", ex.errors().get(0).field());
    }

    @Test
    void inactiveResourceIsRefused() {
        when(repo.organizationOfUser(11)).thenReturn(Optional.of(2L));
        when(repo.organizationExists(2)).thenReturn(true);
        when(repo.resourceActive(6)).thenReturn(false);
        assertEquals("resourceId", assertThrows(ApiException.class,
                () -> service.offer(new OfferRequest(null, 6L, 1, null), 11, "NGO")).errors().get(0).field());
    }

    @Test
    void decisionFollowsTheLifeCycleAndHandlesRaces() {
        when(repo.find(4)).thenReturn(Optional.of(row("OFFERED")));
        when(repo.move(4, "OFFERED", "ACCEPTED", 7)).thenReturn(true);
        service.move(4, "accepted", 7);
        verify(repo).move(4, "OFFERED", "ACCEPTED", 7);

        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.move(4, "RECEIVED", 7)).code());
        when(repo.move(4, "OFFERED", "DECLINED", 7)).thenReturn(false);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.move(4, "DECLINED", 7)).code());
    }
}
