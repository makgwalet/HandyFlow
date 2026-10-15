package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSettings;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSettingsRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthSettingsServiceTest {
    EarthSettingsRepository repo = mock(EarthSettingsRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    InvoicingFacade invoicing = mock(InvoicingFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthSettingsService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();

    @BeforeEach void setUp() {
        service = new EarthSettingsService(repo, invoicing, access, trail);
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(true, new BigDecimal("15.00"), false, null));
        when(repo.findById(tenant.getValue())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void noThresholdIsSetUntilSomeoneSetsIt() {
        granted.add("EARTHMOVING_READ");
        assertNull(service.get(tenant).contractThreshold());
    }
    @Test void readingNeedsReadPermission() {
        assertThrows(HandyFlowException.class, () -> service.get(tenant));
    }
    @Test void onlyAnAdministratorChangesIt() {
        granted.addAll(List.of("EARTHMOVING_READ", "EARTHMOVING_WRITE", "EARTHMOVING_DEPLOY"));
        assertThrows(HandyFlowException.class, () -> service.setContractThreshold(tenant, new BigDecimal("5000"), actor));
        verify(repo, never()).save(any());
    }
    @Test void anAdministratorSetsItAndItIsAudited() {
        granted.add("EARTHMOVING_ADMIN");
        var r = service.setContractThreshold(tenant, new BigDecimal("5000"), actor);
        assertEquals(new BigDecimal("5000.00"), r.contractThreshold());
        verify(trail).record(any(TenantId.class), eq("EARTHMOVING_SETTINGS_CHANGED"), eq("EARTHMOVING_SETTINGS"), any(UUID.class), anyMap(), anyMap());
    }
    @Test void anEmptyValueSwitchesItOffAndANegativeOneIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        EarthSettings existing = EarthSettings.forTenant(tenant);
        existing.setContractThreshold(new BigDecimal("100"), actor);
        when(repo.findById(tenant.getValue())).thenReturn(Optional.of(existing));
        assertNull(service.setContractThreshold(tenant, null, actor).contractThreshold());
        assertThrows(IllegalArgumentException.class, () -> service.setContractThreshold(tenant, new BigDecimal("-5"), actor));
    }
}
