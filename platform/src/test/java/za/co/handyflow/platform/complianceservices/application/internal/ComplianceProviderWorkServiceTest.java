package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ProviderWorkDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceProviderWorkServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceProviderAssignmentRepository assignments;
    @Mock ComplianceProviderDeliverableRepository deliverables;
    @Mock ComplianceProviderRepository providers;
    @Mock ComplianceProviderAccreditationRepository accreditations;
    @Mock ComplianceClientRepository clients;
    @InjectMocks ComplianceProviderWorkService service;

    private ComplianceProvider provider() { return ComplianceProvider.create(TENANT, "Pat", null, "pat@example.com", null, null, null, ME); }

    @Test
    void inactiveProviderCannotBeGivenWork() {
        ComplianceProvider p = provider();
        p.deactivate("Left the register");
        UUID client = UUID.randomUUID();
        when(clients.findByIdForTenant(TENANT, client)).thenReturn(Optional.of(mock(ComplianceClient.class)));
        when(providers.findOne(TENANT, p.getId())).thenReturn(Optional.of(p));
        AssignmentRequest r = new AssignmentRequest(p.getId(), "Fire audit", null, null, null, true, null);
        assertThatThrownBy(() -> service.propose(TENANT, client, r, ME, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(assignments, never()).save(any());
    }

    @Test
    void activationRefusedWithoutUsableAccreditation() {
        ComplianceProvider p = provider();
        ComplianceProviderAssignment a = ComplianceProviderAssignment.propose(TENANT, p.getId(), UUID.randomUUID(), "Audit", null, null, null, true, null, TODAY, ME);
        a.declare(false, "No relationship with this provider", ME);
        when(assignments.findOne(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(providers.findOne(TENANT, a.getProviderId())).thenReturn(Optional.of(p));
        when(accreditations.findForProvider(TENANT, p.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.activate(TENANT, a.getId(), ME, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(assignments, never()).save(any());
    }

    @Test
    void completionRefusedWhileDeliverableUnreviewed() {
        ComplianceProviderAssignment a = ComplianceProviderAssignment.propose(TENANT, UUID.randomUUID(), UUID.randomUUID(), "Audit", null, null, null, false, "Sole practitioner, no body exists", TODAY, ME);
        a.declare(false, "No relationship with this provider", ME);
        a.activate(ME, true, false);
        ComplianceProviderDeliverable d = ComplianceProviderDeliverable.expect(TENANT, a.getId(), "Report", null);
        d.received(ME);
        when(assignments.findOne(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(deliverables.findForAssignment(TENANT, a.getId())).thenReturn(List.of(d));
        assertThatThrownBy(() -> service.complete(TENANT, a.getId(), 4, "Finished and reviewed fully", ME, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(assignments, never()).save(any());
    }

    @Test
    void performanceHasNoAverageWithoutFinishedWork() {
        ComplianceProvider p = provider();
        when(providers.findOne(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(assignments.findForProvider(TENANT, p.getId())).thenReturn(List.of());
        PerformanceView v = service.performance(TENANT, p.getId());
        assertThat(v.averageRating() == null).isTrue();
        assertThat(v.completed() == 0).isTrue();
    }
}
