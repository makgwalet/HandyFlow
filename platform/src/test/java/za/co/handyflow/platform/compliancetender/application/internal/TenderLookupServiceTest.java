package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.domain.model.TenderLookupValue;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderLookupValueRepository;
import za.co.handyflow.platform.compliancetender.dto.TenderLookupValueResponse;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenderLookupServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID user = UUID.randomUUID();
    private final TenderLookupValueRepository repo = mock(TenderLookupValueRepository.class);
    private final TenderLookupService service = new TenderLookupService(repo);

    @Test
    @DisplayName("cleaning trims and collapses spaces")
    void clean() {
        assertThat(TenderLookupService.clean("  Water   Board  ")).isEqualTo("Water Board");
        assertThat(TenderLookupService.clean(null)).isEqualTo("");
    }

    @Test
    @DisplayName("a new value is saved cleaned")
    void addsNew() {
        when(repo.findByValue(tenant, "TENDER_AUTHORITIES", "Rand Water")).thenReturn(Optional.empty());
        when(repo.save(any(TenderLookupValue.class))).thenAnswer(inv -> inv.getArgument(0));
        TenderLookupValueResponse r = service.add(tenant, "TENDER_AUTHORITIES", "  Rand   Water ", user);
        assertThat(r.value()).isEqualTo("Rand Water");
        assertThat(r.listKey()).isEqualTo("TENDER_AUTHORITIES");
    }

    @Test
    @DisplayName("adding a value that is already there returns it and saves nothing")
    void addsExisting() {
        TenderLookupValue existing = TenderLookupValue.create(tenant, "UNITS", "bag", user);
        when(repo.findByValue(tenant, "UNITS", "BAG")).thenReturn(Optional.of(existing));
        assertThat(service.add(tenant, "UNITS", "BAG", user).value()).isEqualTo("bag");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("an unknown list and an empty value are refused")
    void refuses() {
        assertThatThrownBy(() -> service.add(tenant, "NOPE", "x", user)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(tenant, "UNITS", "   ", user)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(tenant, "UNITS", "x".repeat(101), user)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("removing something that is not this company's is not found")
    void removeMissing() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdForTenant(tenant, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(tenant, id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
