package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDeliverySite;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSiteResource;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSiteResourceEvent;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.ResourceDtos.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvResourceServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock private TrainProvSiteResourceRepository resources;
    @Mock private TrainProvSiteResourceEventRepository events;
    @Mock private TrainProvDeliverySiteRepository sites;
    @Mock private TrainProvVenueRepository venues;
    @Mock private TrainProvNumberGenerator numbers;
    @InjectMocks private TrainProvResourceService service;

    private TrainProvSiteResource resource() {
        return TrainProvSiteResource.create(TENANT, "RES-00001", UUID.randomUUID(), null, "Welding rig", "EQUIPMENT", "WR-1", 1, false, null, USER);
    }

    @Test
    void sameIdentifierOnAnotherLiveResourceIsRefused() {
        TrainProvDeliverySite site = mock(TrainProvDeliverySite.class);
        when(site.getId()).thenReturn(UUID.randomUUID());
        when(site.getStatus()).thenReturn("ACTIVE");
        when(sites.findForTenant(any(), any())).thenReturn(Optional.of(site));
        when(resources.identifierTaken(TENANT, "WR-1", null)).thenReturn(true);
        ResourceRequest r = new ResourceRequest(UUID.randomUUID(), null, "Welding rig", "EQUIPMENT", "WR-1", 1, false, null);
        assertThatThrownBy(() -> service.create(TENANT, r, USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already has that identifier");
        verify(resources, never()).save(any());
    }

    @Test
    void inactiveSiteCannotReceiveANewResource() {
        TrainProvDeliverySite site = mock(TrainProvDeliverySite.class);
        when(site.getStatus()).thenReturn("RETIRED");
        when(sites.findForTenant(any(), any())).thenReturn(Optional.of(site));
        ResourceRequest r = new ResourceRequest(UUID.randomUUID(), null, "Welding rig", "EQUIPMENT", null, 1, false, null);
        assertThatThrownBy(() -> service.create(TENANT, r, USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("not active");
    }

    @Test
    void stateChangeIsWrittenToHistoryWithItsReason() {
        TrainProvSiteResource res = resource();
        when(resources.findForTenant(TENANT, res.getId())).thenReturn(Optional.of(res));
        when(sites.findForTenant(any(), any())).thenReturn(Optional.empty());
        service.changeStatus(TENANT, res.getId(), new StatusRequest("UNDER_REPAIR", "Hydraulic leak found on the arm", TODAY), USER, TODAY);
        assertThat(res.getStatus()).isEqualTo("UNDER_REPAIR");
        ArgumentCaptor<TrainProvSiteResourceEvent> ev = ArgumentCaptor.forClass(TrainProvSiteResourceEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getFromStatus()).isEqualTo("IN_SERVICE");
        assertThat(ev.getValue().getNote()).isEqualTo("Hydraulic leak found on the arm");
    }

    @Test
    void retiredResourceStaysRetiredAndWritesNoEvent() {
        TrainProvSiteResource res = resource();
        res.setStatus("RETIRED", "Scrapped after the safety review", TODAY, TODAY);
        when(resources.findForTenant(TENANT, res.getId())).thenReturn(Optional.of(res));
        assertThatThrownBy(() -> service.changeStatus(TENANT, res.getId(), new StatusRequest("IN_SERVICE", "Found it again today", TODAY), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("stays retired");
        verify(events, never()).save(any());
    }

    @Test
    void movingToTheSameSiteIsRefused() {
        TrainProvSiteResource res = resource();
        TrainProvDeliverySite site = mock(TrainProvDeliverySite.class);
        when(site.getId()).thenReturn(res.getSiteId());
        when(site.getStatus()).thenReturn("ACTIVE");
        when(resources.findForTenant(TENANT, res.getId())).thenReturn(Optional.of(res));
        when(sites.findForTenant(TENANT, res.getSiteId())).thenReturn(Optional.of(site));
        assertThatThrownBy(() -> service.move(TENANT, res.getId(), new MoveRequest(res.getSiteId(), null, "Needed for the new cohort", TODAY), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already at that site");
    }
}
