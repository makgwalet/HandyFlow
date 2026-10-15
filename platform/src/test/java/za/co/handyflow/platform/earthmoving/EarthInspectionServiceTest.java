package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EarthInspectionServiceTest {
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthInspectionRepository inspections = mock(EarthInspectionRepository.class);
    EarthInspectionItemRepository items = mock(EarthInspectionItemRepository.class);
    EarthmovingIncidentService incidents = mock(EarthmovingIncidentService.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthInspectionService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    EarthAsset asset;

    @BeforeEach
    void setUp() {
        service = new EarthInspectionService(assets, inspections, items, incidents, access, trail);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null,
                "OWN", null, null, null, null, null, null);
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
        when(inspections.save(any())).thenAnswer(i -> i.getArgument(0));
        when(items.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidents.reportIncident(any(TenantId.class), any(), any())).thenAnswer(i -> {
            IncidentResponse r = mock(IncidentResponse.class);
            when(r.id()).thenReturn(UUID.randomUUID());
            return r;
        });
        granted.add("EARTHMOVING_WRITE");
    }

    CreateInspectionRequest request(Map<String, String[]> findings) {
        List<CreateInspectionRequest.Item> list = new ArrayList<>();
        for (String item : InspectionRules.CHECKLIST) {
            String[] f = findings.get(item);
            list.add(new CreateInspectionRequest.Item(item, f == null ? "OK" : f[0], f == null ? null : f[1]));
        }
        return new CreateInspectionRequest(asset.getId(), null, null, list, null);
    }

    @Test void aCleanInspectionRaisesNothing() {
        InspectionResponse r = service.create(tenant, request(Map.of()), actor);
        assertEquals("PASS", r.result());
        verify(incidents, never()).reportIncident(any(TenantId.class), any(), any());
    }

    @Test void anUnsafeFindingIsReportedAsACriticalBreakdown() {
        InspectionResponse r = service.create(tenant, request(Map.of("Brakes", new String[]{"UNSAFE", "Pedal goes to the floor"})), actor);
        assertEquals("UNSAFE", r.result());
        verify(incidents).reportIncident(eq(tenant), argThat(q -> "BREAKDOWN".equals(q.type()) && "CRITICAL".equals(q.severity())
                && q.title().contains("Brakes")), eq(actor));
        assertNotNull(r.items().stream().filter(x -> x.item().equals("Brakes")).findFirst().orElseThrow().incidentId());
    }

    @Test void aDefectIsTrackedWithoutStoppingTheMachine() {
        InspectionResponse r = service.create(tenant, request(Map.of("Tyres or tracks", new String[]{"DEFECT", "Cut in the sidewall"})), actor);
        assertEquals("DEFECTS", r.result());
        verify(incidents).reportIncident(eq(tenant), argThat(q -> "OTHER".equals(q.type()) && "MEDIUM".equals(q.severity())), eq(actor));
    }

    @Test void recordingNeedsWritePermission() {
        granted.clear();
        assertThrows(HandyFlowException.class, () -> service.create(tenant, request(Map.of()), actor));
        verify(inspections, never()).save(any());
    }

    @Test void anIncompleteChecklistIsRefusedAndNothingIsSaved() {
        CreateInspectionRequest partial = new CreateInspectionRequest(asset.getId(), null, null,
                List.of(new CreateInspectionRequest.Item("Brakes", "OK", null)), null);
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, partial, actor));
        verify(inspections, never()).save(any());
    }

    @Test void latestGivesOneRowPerMachineAndNeedsReadPermission() {
        EarthInspection older = EarthInspection.create(tenant, asset.getId(), actor, null, "PASS", null);
        EarthInspection newer = EarthInspection.create(tenant, asset.getId(), actor, null, "UNSAFE", null);
        when(inspections.findLatestPerAsset(tenant)).thenReturn(List.of(older, newer));
        granted.add("EARTHMOVING_READ");
        List<LatestInspectionResponse> r = service.latest(tenant);
        assertEquals(1, r.size());
        assertEquals(asset.getId(), r.get(0).assetId());
        granted.clear();
        assertThrows(HandyFlowException.class, () -> service.latest(tenant));
    }

    @Test void anInspectionIsPreUseUnlessItSaysOtherwise() {
        assertEquals("PRE_USE", service.create(tenant, request(Map.of()), actor).inspectionType());
    }

    @Test void aHandoverOrReturnNeedsAMeterReadingAndIsKeptWithItsType() {
        CreateInspectionRequest base = request(Map.of());
        CreateInspectionRequest noMeter = new CreateInspectionRequest(asset.getId(), null, null, base.items(), "return");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, noMeter, actor));
        CreateInspectionRequest ok = new CreateInspectionRequest(asset.getId(), new java.math.BigDecimal("1250.5"), null, base.items(), "return");
        assertEquals("RETURN", service.create(tenant, ok, actor).inspectionType());
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant,
                new CreateInspectionRequest(asset.getId(), null, null, base.items(), "SERVICE"), actor));
    }
}
