package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthDeployCheckServiceTest {
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthMaintenancePlanRepository plans = mock(EarthMaintenancePlanRepository.class);
    EarthInspectionRepository inspections = mock(EarthInspectionRepository.class);
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
        service = new EarthDeployCheckService(assets, plans, inspections, access);
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
}
