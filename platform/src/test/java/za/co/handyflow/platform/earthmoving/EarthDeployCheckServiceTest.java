package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthDeployCheckServiceTest {
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthMaintenancePlanRepository plans = mock(EarthMaintenancePlanRepository.class);
    EarthInspectionRepository inspections = mock(EarthInspectionRepository.class);
    EarthSiteRepository sites = mock(EarthSiteRepository.class);
    ContractingFacade contracting = mock(ContractingFacade.class);
    EarthSettingsService settings = mock(EarthSettingsService.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthDeployCheckService service;
    TenantId tenant = TenantId.generate();
    EarthAsset asset;
    static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

    @BeforeEach
    void setUp() {
        service = new EarthDeployCheckService(assets, plans, inspections, sites, contracting, settings, access);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null,
                "OWN", null, null, null, null, null, null);
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
        when(plans.findForAsset(any(), any())).thenReturn(List.of());
        when(inspections.findRecent(any(), any(), any())).thenReturn(List.of());
        granted.add("EARTHMOVING_READ");
    }

    @Test void neverInspectedGivesAWarningButNoError() {
        assertEquals(List.of("This machine has never been inspected."), service.warnings(tenant, asset.getId(), NOW).warnings());
    }
    @Test void aRecentPassingInspectionGivesNoWarnings() {
        when(inspections.findRecent(any(), any(), any())).thenReturn(List.of(EarthInspection.create(tenant, asset.getId(), null, null, "PASS", null)));
        assertTrue(service.warnings(tenant, asset.getId(), Instant.now()).warnings().isEmpty());
    }
    @Test void anUnknownMachineIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.warnings(tenant, UUID.randomUUID(), NOW));
    }
    @Test void readingNeedsReadPermission() {
        granted.clear();
        assertThrows(HandyFlowException.class, () -> service.warnings(tenant, asset.getId(), NOW));
    }

    // ---- hire contract warnings ----
    private EarthAsset rented() {
        EarthAsset a = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null,
                "OWN", null, null, null, new BigDecimal("1000"), null, null);
        when(assets.findActiveById(tenant, a.getId())).thenReturn(Optional.of(a));
        return a;
    }
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private ContractSummary contract(String status) {
        return new ContractSummary(UUID.randomUUID(), "CON-0007", "Hire", "EQUIPMENT_HIRE", status, FROM, FROM.plusDays(9), null, false, null);
    }

    @Test void aBigHireWithNoContractGetsAWarning() {
        EarthAsset a = rented();
        when(settings.contractThreshold(tenant)).thenReturn(new BigDecimal("5000"));
        var hire = new EarthDeployCheckService.HireContext(UUID.randomUUID(), null, FROM, FROM.plusDays(9), null);
        var w = service.warnings(tenant, a.getId(), NOW, hire).warnings();
        assertTrue(w.stream().anyMatch(s -> s.contains("R10000.00") && s.contains("no contract is linked")));
    }
    @Test void aSmallHireOrNoThresholdGivesNoContractWarning() {
        EarthAsset a = rented();
        var hire = new EarthDeployCheckService.HireContext(UUID.randomUUID(), null, FROM, FROM.plusDays(9), null);
        when(settings.contractThreshold(tenant)).thenReturn(null);
        assertFalse(service.warnings(tenant, a.getId(), NOW, hire).warnings().stream().anyMatch(s -> s.contains("contract")));
        when(settings.contractThreshold(tenant)).thenReturn(new BigDecimal("50000"));
        assertFalse(service.warnings(tenant, a.getId(), NOW, hire).warnings().stream().anyMatch(s -> s.contains("contract")));
    }
    @Test void anUnsignedContractIsReportedAndASignedOneIsNot() {
        EarthAsset a = rented();
        when(settings.contractThreshold(tenant)).thenReturn(new BigDecimal("5000"));
        ContractSummary draft = contract("DRAFT");
        when(contracting.findById(tenant, draft.id())).thenReturn(Optional.of(draft));
        var hire = new EarthDeployCheckService.HireContext(UUID.randomUUID(), null, FROM, FROM.plusDays(9), draft.id());
        assertTrue(service.warnings(tenant, a.getId(), NOW, hire).warnings().stream().anyMatch(s -> s.contains("CON-0007") && s.contains("still a draft")));
        ContractSummary signed = contract("SIGNED");
        when(contracting.findById(tenant, signed.id())).thenReturn(Optional.of(signed));
        hire = new EarthDeployCheckService.HireContext(UUID.randomUUID(), null, FROM, FROM.plusDays(9), signed.id());
        assertFalse(service.warnings(tenant, a.getId(), NOW, hire).warnings().stream().anyMatch(s -> s.contains("contract")));
    }
    @Test void aSavedSitesCustomerIsUsedWhenNoCustomerIsGiven() {
        EarthAsset a = rented();
        when(settings.contractThreshold(tenant)).thenReturn(new BigDecimal("5000"));
        UUID customer = UUID.randomUUID();
        EarthSite site = EarthSite.create(tenant, "Quarry", null, null, null, customer, null, null, null);
        when(sites.findByTenantAndId(tenant, site.getId())).thenReturn(Optional.of(site));
        var hire = new EarthDeployCheckService.HireContext(null, site.getId(), FROM, FROM.plusDays(9), null);
        assertTrue(service.warnings(tenant, a.getId(), NOW, hire).warnings().stream().anyMatch(s -> s.contains("no contract is linked")));
    }
    @Test void aHireNotForACustomerNeverNeedsAContract() {
        EarthAsset a = rented();
        when(settings.contractThreshold(tenant)).thenReturn(new BigDecimal("1"));
        var hire = new EarthDeployCheckService.HireContext(null, null, FROM, FROM.plusDays(9), null);
        assertFalse(service.warnings(tenant, a.getId(), NOW, hire).warnings().stream().anyMatch(s -> s.contains("contract")));
    }
    @Test void aContractThatDoesNotExistIsRefused() {
        UUID id = UUID.randomUUID();
        when(contracting.findById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.requireContract(tenant, id));
    }
}
