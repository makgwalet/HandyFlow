package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelCustomerServiceTest {

    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelCustomerSiteRepository sites;
    @Mock CrmFacade crm;
    @InjectMocks FuelCustomerService service;

    final TenantId tenant = TenantId.of(UUID.randomUUID());
    final UUID crmId = UUID.randomUUID();

    private CustomerAccountRequest req(String type, String limit, int terms) {
        return new CustomerAccountRequest(crmId, type, new BigDecimal(limit), terms, null, null);
    }

    @Test @DisplayName("account needs an existing CRM customer")
    void unknownCustomer() {
        when(crm.customerExists(tenant, crmId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.create(tenant, req("MINING_COMPANY", "1000", 30)));
        verify(accounts, never()).save(any());
    }

    @Test @DisplayName("one fuel account per customer")
    void duplicate() {
        when(crm.customerExists(tenant, crmId)).thenReturn(true);
        when(accounts.findByCrmCustomerId(tenant, crmId))
                .thenReturn(Optional.of(FuelCustomerAccount.create(tenant, crmId, "OTHER", BigDecimal.ZERO, 30, null, null)));
        assertThrows(ConflictException.class, () -> service.create(tenant, req("MINING_COMPANY", "1000", 30)));
    }

    @Test @DisplayName("unknown type, negative limit and silly terms are refused")
    void domainRules() {
        assertThrows(IllegalArgumentException.class, () -> FuelCustomerAccount.create(tenant, crmId, "BOGUS", BigDecimal.ONE, 30, null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelCustomerAccount.create(tenant, crmId, "FARM", BigDecimal.ONE, 30, null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelCustomerAccount.create(tenant, crmId, "COMMERCIAL_FARM", new BigDecimal("-1"), 30, null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelCustomerAccount.create(tenant, crmId, "COMMERCIAL_FARM", BigDecimal.ONE, 400, null, null));
    }

    @Test @DisplayName("credit hold needs a reason; release clears it")
    void creditHold() {
        FuelCustomerAccount a = FuelCustomerAccount.create(tenant, crmId, "COMMERCIAL_FARM", BigDecimal.TEN, 30, null, null);
        assertThrows(IllegalArgumentException.class, () -> a.placeOnHold("no"));
        a.placeOnHold("Overdue 60 days");
        assertTrue(a.isCreditHold());
        a.releaseHold();
        assertFalse(a.isCreditHold());
        assertNull(a.getCreditHoldReason());
    }

    @Test @DisplayName("another tenant's account is not found")
    void tenantIsolation() {
        UUID id = UUID.randomUUID();
        when(accounts.findActiveById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(tenant, id));
        assertThrows(ResourceNotFoundException.class, () -> service.addSite(tenant, id,
                new CustomerSiteRequest("Pit 1", "Farm road", null, null, null, null)));
        verify(sites, never()).save(any());
    }

    @Test @DisplayName("site of another tenant cannot be edited or removed")
    void siteIsolation() {
        UUID id = UUID.randomUUID();
        when(sites.findActiveById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.removeSite(tenant, id));
        assertThrows(ResourceNotFoundException.class, () -> service.updateSite(tenant, id,
                new CustomerSiteRequest("Pit 1", "Farm road", null, null, null, null)));
    }
}
