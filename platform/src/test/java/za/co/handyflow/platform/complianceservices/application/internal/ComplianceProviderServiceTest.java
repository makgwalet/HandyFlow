package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ProviderDtos.*;
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
class ComplianceProviderServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceProviderRepository providers;
    @Mock ComplianceProviderServiceLinkRepository links;
    @Mock ComplianceProviderAccreditationRepository accreditations;
    @InjectMocks ComplianceProviderService service;

    private ComplianceProvider provider(String email) { return ComplianceProvider.create(TENANT, "Pat", null, email, null, null, null, ME); }

    @Test
    void duplicateEmailIsRefused() {
        ComplianceProvider existing = provider("pat@example.com");
        when(providers.findByEmail(TENANT, "pat@example.com")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(TENANT, new ProviderRequest("Pat Two", null, "pat@example.com", null, null, null), ME, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(providers, never()).save(any());
    }

    @Test
    void createSavesAndShowsNothingUsableYet() {
        when(providers.save(any(ComplianceProvider.class))).thenAnswer(i -> i.getArgument(0));
        ProviderDetail d = service.create(TENANT, new ProviderRequest("Pat", "FIRM", null, null, null, null), ME, TODAY);
        assertThat(d.provider().usableAccreditations() == 0).isTrue();
        assertThat(d.provider().kind().equals("FIRM")).isTrue();
    }

    @Test
    void checkingYourOwnEntryIsAConflict() {
        ComplianceProvider p = provider(null);
        ComplianceProviderAccreditation a = ComplianceProviderAccreditation.claim(TENANT, p.getId(), "SANAS", "B-1", null, null, ME);
        when(accreditations.findOne(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.check(TENANT, a.getId(), true, "Matched on the register", ME, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(accreditations, never()).save(any());
    }

    @Test
    void anotherPersonCheckingMakesItUsable() {
        ComplianceProvider p = provider(null);
        ComplianceProviderAccreditation a = ComplianceProviderAccreditation.claim(TENANT, p.getId(), "SANAS", "B-1", null, TODAY.plusDays(100), ME);
        when(accreditations.findOne(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(providers.findOne(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(accreditations.findForProvider(TENANT, p.getId())).thenReturn(List.of(a));
        when(links.findForProvider(TENANT, p.getId())).thenReturn(List.of());
        ProviderDetail d = service.check(TENANT, a.getId(), true, "Matched on the register", OTHER, TODAY);
        assertThat(d.provider().usableAccreditations() == 1).isTrue();
        assertThat(d.accreditations().get(0).usable()).isTrue();
    }

    @Test
    void servicesAreReplacedNotDuplicated() {
        ComplianceProvider p = provider(null);
        ComplianceProviderServiceLink keep = ComplianceProviderServiceLink.of(TENANT, p.getId(), "CS-A");
        ComplianceProviderServiceLink drop = ComplianceProviderServiceLink.of(TENANT, p.getId(), "CS-OLD");
        when(providers.findOne(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(links.findForProvider(TENANT, p.getId())).thenReturn(List.of(keep, drop));
        when(accreditations.findForProvider(TENANT, p.getId())).thenReturn(List.of());
        service.setServices(TENANT, p.getId(), List.of("CS-A", "CS-NEW"), TODAY);
        verify(links).delete(drop);
        verify(links, never()).delete(keep);
        verify(links, times(1)).save(any(ComplianceProviderServiceLink.class));
    }
}
