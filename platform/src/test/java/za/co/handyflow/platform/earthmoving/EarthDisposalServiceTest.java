package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.DisposalNoteRequest;
import za.co.handyflow.platform.earthmoving.dto.DisposalResponse;
import za.co.handyflow.platform.earthmoving.dto.RequestDisposalRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EarthDisposalServiceTest {
    EarthDisposalRequestRepository requests = mock(EarthDisposalRequestRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthDeploymentRepository deployments = mock(EarthDeploymentRepository.class);
    EarthHireChargeRepository charges = mock(EarthHireChargeRepository.class);
    EarthStatusEventRepository events = mock(EarthStatusEventRepository.class);
    DocumentsFacade documents = mock(DocumentsFacade.class);
    EarthAlertService alerts = mock(EarthAlertService.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthDisposalService service;
    TenantId tenant = TenantId.generate();
    UUID asker = UUID.randomUUID(), boss = UUID.randomUUID();
    EarthAsset machine;

    @BeforeEach
    void setUp() {
        service = new EarthDisposalService(requests, assets, deployments, charges, events, documents, alerts, trail, access);
        machine = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", "Caterpillar", "D9T", 2020, "S1", "GP1",
                "OWN", null, null, null, new BigDecimal("18500"), new BigDecimal("2200"), null);
        when(assets.findActiveById(tenant, machine.getId())).thenReturn(Optional.of(machine));
        when(deployments.findOpenForAsset(tenant, machine.getId())).thenReturn(Optional.empty());
        when(charges.countPendingForAsset(tenant, machine.getId())).thenReturn(0L);
        when(requests.findLiveForAsset(tenant, machine.getId())).thenReturn(Optional.empty());
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documents.countActive(eq(tenant), anyString(), any(), anyString())).thenReturn(1L);
    }

    private EarthDisposalRequest stored(String reason) {
        EarthDisposalRequest r = EarthDisposalRequest.request(tenant, machine.getId(), reason, "Engine seized, beyond repair", "Buyer", null, asker);
        when(requests.findByTenantAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private RequestDisposalRequest ask() { return new RequestDisposalRequest(machine.getId(), "SCRAPPED", "Engine seized, beyond repair", null, null); }

    @Test void askingNeedsWriteAuthority() {
        assertThrows(HandyFlowException.class, () -> service.request(tenant, ask(), asker));
        verify(requests, never()).save(any());
    }

    @Test void askingRecordsTheRequestAndRaisesAnApprovalAlert() {
        granted.add("EARTHMOVING_WRITE");
        DisposalResponse out = service.request(tenant, ask(), asker);
        assertEquals("REQUESTED", out.status());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_DISPOSAL_REQUESTED"), eq("EARTHMOVING_DISPOSAL"), any(), any(), any());
        verify(alerts).raise(eq(tenant), eq("DISPOSAL_APPROVAL"), eq("WARNING"), eq(machine.getId()), eq("DISPOSAL"), any(), anyString(), anyString(), anyString(), any());
    }

    @Test void aMachineOnHireOrWithUnbilledChargesCannotBeAskedAbout() {
        granted.add("EARTHMOVING_WRITE");
        when(charges.countPendingForAsset(tenant, machine.getId())).thenReturn(2L);
        var e = assertThrows(IllegalStateException.class, () -> service.request(tenant, ask(), asker));
        assertTrue(e.getMessage().contains("not been invoiced"));
        when(charges.countPendingForAsset(tenant, machine.getId())).thenReturn(0L);
        when(deployments.findOpenForAsset(tenant, machine.getId())).thenReturn(Optional.of(mock(EarthDeployment.class)));
        assertThrows(IllegalStateException.class, () -> service.request(tenant, ask(), asker));
        verify(requests, never()).save(any());
    }

    @Test void onlyOneLiveRequestPerMachine() {
        granted.add("EARTHMOVING_WRITE");
        when(requests.findLiveForAsset(tenant, machine.getId())).thenReturn(Optional.of(stored("SCRAPPED")));
        assertThrows(IllegalStateException.class, () -> service.request(tenant, ask(), asker));
    }

    @Test void approvingNeedsAdminAndSomeoneElse() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_WRITE");
        assertThrows(HandyFlowException.class, () -> service.approve(tenant, r.getId(), null, boss));
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalStateException.class, () -> service.approve(tenant, r.getId(), null, asker));
        assertEquals("REQUESTED", r.getStatus());
    }

    @Test void approvingNeedsEvidence() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_ADMIN");
        when(documents.countActive(tenant, "EARTH_DISPOSAL", r.getId(), "DISPOSAL_EVIDENCE")).thenReturn(0L);
        var e = assertThrows(IllegalStateException.class, () -> service.approve(tenant, r.getId(), null, boss));
        assertTrue(e.getMessage().contains("evidence"));
        verify(alerts, never()).resolveByKey(any(), anyString(), any(), anyString());
    }

    @Test void approvingSettlesTheAlertAndIsAudited() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_ADMIN");
        DisposalResponse out = service.approve(tenant, r.getId(), new DisposalNoteRequest("Go ahead"), boss);
        assertEquals("APPROVED", out.status());
        verify(alerts).resolveByKey(eq(tenant), eq("DISPOSAL:" + r.getId()), eq(boss), anyString());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_DISPOSAL_APPROVED"), anyString(), eq(r.getId()), any(), any());
    }

    @Test void completingRetiresTheMachineAndRecordsTheHistory() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_ADMIN");
        service.approve(tenant, r.getId(), null, boss);
        DisposalResponse out = service.complete(tenant, r.getId(), boss);
        assertEquals("COMPLETED", out.status());
        assertEquals(AssetStatus.RETIRED, machine.getStatus());
        verify(assets).save(machine);
        verify(events).save(any(EarthStatusEvent.class));
        verify(trail).record(eq(tenant), eq("EARTHMOVING_DISPOSAL_COMPLETED"), anyString(), eq(r.getId()), any(), any());
    }

    @Test void completingChecksAgainThatNothingIsRunningOnTheMachine() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_ADMIN");
        service.approve(tenant, r.getId(), null, boss);
        when(deployments.findOpenForAsset(tenant, machine.getId())).thenReturn(Optional.of(mock(EarthDeployment.class)));
        assertThrows(IllegalStateException.class, () -> service.complete(tenant, r.getId(), boss));
        assertNotEquals(AssetStatus.RETIRED, machine.getStatus());
        verify(events, never()).save(any());
    }

    @Test void aRequestThatWasNeverApprovedCannotBeCompleted() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalStateException.class, () -> service.complete(tenant, r.getId(), boss));
        assertNotEquals(AssetStatus.RETIRED, machine.getStatus());
    }

    @Test void rejectingNeedsAReason() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.reject(tenant, r.getId(), new DisposalNoteRequest(" "), boss));
        assertEquals("REJECTED", service.reject(tenant, r.getId(), new DisposalNoteRequest("Still earning"), boss).status());
    }

    @Test void onlyTheRequesterOrAnAdminCanCancel() {
        EarthDisposalRequest r = stored("SCRAPPED");
        granted.add("EARTHMOVING_WRITE");
        assertThrows(HandyFlowException.class, () -> service.cancel(tenant, r.getId(), new DisposalNoteRequest("Changed mind"), boss));
        assertEquals("CANCELLED", service.cancel(tenant, r.getId(), new DisposalNoteRequest("Changed mind"), asker).status());
        verify(alerts).resolveByKey(eq(tenant), eq("DISPOSAL:" + r.getId()), eq(asker), anyString());
    }
}
