package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDeliverySite;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPractitioner;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSitePermission;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.SitePermissionDtos.PermissionRequest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvSitePermissionServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock private TrainProvSitePermissionRepository permissions;
    @Mock private TrainProvPractitionerRepository practitioners;
    @Mock private TrainProvDeliverySiteRepository sites;
    @Mock private TrainProvProgrammeRepository programmes;
    @InjectMocks private TrainProvSitePermissionService service;

    private PermissionRequest request(UUID person, UUID site) {
        return new PermissionRequest(person, site, "FACILITATOR", null, TODAY, null, "Placed at this site for the new intake");
    }

    @Test
    void recordsAPermissionForAnActivePersonAtAnActiveSite() {
        TrainProvPractitioner person = mock(TrainProvPractitioner.class);
        when(person.getId()).thenReturn(UUID.randomUUID());
        when(person.getStatus()).thenReturn("ACTIVE");
        TrainProvDeliverySite site = mock(TrainProvDeliverySite.class);
        when(site.getId()).thenReturn(UUID.randomUUID());
        when(site.getStatus()).thenReturn("ACTIVE");
        UUID pid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        when(practitioners.findForTenant(TENANT, pid)).thenReturn(Optional.of(person));
        when(sites.findForTenant(TENANT, sid)).thenReturn(Optional.of(site));
        when(permissions.liveExists(any(), any(), any(), any(), any())).thenReturn(false);
        service.create(TENANT, request(pid, sid), USER, TODAY);
        verify(permissions).save(any(TrainProvSitePermission.class));
    }

    @Test
    void secondLivePermissionForTheSameScopeIsRefused() {
        TrainProvPractitioner person = mock(TrainProvPractitioner.class);
        when(person.getId()).thenReturn(UUID.randomUUID());
        when(person.getStatus()).thenReturn("ACTIVE");
        TrainProvDeliverySite site = mock(TrainProvDeliverySite.class);
        when(site.getId()).thenReturn(UUID.randomUUID());
        when(site.getStatus()).thenReturn("ACTIVE");
        UUID pid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        when(practitioners.findForTenant(TENANT, pid)).thenReturn(Optional.of(person));
        when(sites.findForTenant(TENANT, sid)).thenReturn(Optional.of(site));
        when(permissions.liveExists(any(), any(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, request(pid, sid), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already has a live permission");
        verify(permissions, never()).save(any());
    }

    @Test
    void withdrawalKeepsTheRecordWithItsReason() {
        TrainProvSitePermission p = TrainProvSitePermission.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), "FACILITATOR", null, TODAY, null, "Placed at this site for the new intake", USER);
        when(permissions.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(practitioners.findForTenant(any(), any())).thenReturn(Optional.empty());
        when(sites.findForTenant(any(), any())).thenReturn(Optional.empty());
        service.withdraw(TENANT, p.getId(), "Moved to the northern branch", USER, TODAY);
        assertThat(p.getStatus()).isEqualTo("WITHDRAWN");
        verify(permissions, never()).delete(any());
    }
}
