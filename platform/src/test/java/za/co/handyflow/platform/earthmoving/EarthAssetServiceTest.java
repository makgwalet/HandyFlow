package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
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

class EarthAssetServiceTest {
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    MaintenanceRepository maintenance = mock(MaintenanceRepository.class);
    OperatorLogRepository logs = mock(OperatorLogRepository.class);
    EarthDeploymentRepository deployments = mock(EarthDeploymentRepository.class);
    EarthStatusEventRepository events = mock(EarthStatusEventRepository.class);
    EarthmovingIncidentRepository incidents = mock(EarthmovingIncidentRepository.class);
    EarthWorkOrderRepository workOrders = mock(EarthWorkOrderRepository.class);
    EarthAlertService alerts = mock(EarthAlertService.class);
    EarthSiteService sites = mock(EarthSiteService.class);
    EarthMeterService meters = mock(EarthMeterService.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    NotificationService notifications = mock(NotificationService.class);
    TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthAssetService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EarthAssetService(assets, maintenance, logs, deployments, events, incidents, workOrders, access, trail,
                notifications, admins, alerts, sites, meters);
        when(assets.save(any())).thenAnswer(i -> i.getArgument(0));
        when(assets.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of());
        when(sites.supervisorForAsset(any(), any())).thenReturn(java.util.Optional.empty());
        // A stand-in for the real resolver: typed names pass straight through, no links.
        when(sites.resolveForDeploy(any(), any())).thenAnswer(i -> {
            DeployAssetRequest r = i.getArgument(1);
            return new EarthSiteService.DeployTarget(r.siteId(), r.siteName() == null ? null : r.siteName().trim(),
                    r.customerId(), r.clientName(), r.projectId());
        });
    }

    private EarthAsset machine(AssetStatus status) {
        EarthAsset a = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", "Caterpillar", "D9T", 2020, "S1", "GP1",
                "OWN", null, null, null, new BigDecimal("18500"), new BigDecimal("2200"), null);
        if (status == AssetStatus.MAINTENANCE) a.sendToMaintenance();
        if (status == AssetStatus.BREAKDOWN) a.breakdown();
        if (status == AssetStatus.DEPLOYED) a.deployTo("Mine A", "Anglo");
        when(assets.findActiveById(tenant, a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    /** A work order completed by someone else, just now: the evidence a release needs. */
    private void evidenceFor(EarthAsset a, UUID completedBy) {
        var wo = mock(za.co.handyflow.platform.earthmoving.domain.model.EarthWorkOrder.class);
        when(wo.getId()).thenReturn(UUID.randomUUID());
        when(wo.getCompletedAt()).thenReturn(java.time.Instant.now());
        when(wo.getCompletedBy()).thenReturn(completedBy);
        when(workOrders.findCompletedForAsset(eq(tenant), eq(a.getId()), any())).thenReturn(List.of(wo));
    }

    private UpdateAssetStatusRequest status(String to, String note) { return new UpdateAssetStatusRequest(to, note); }

    // ── release ──────────────────────────────────────────────────────────────

    @Test
    void releaseNeedsTheReleaseAuthorityEvenForAnAdmin() {
        EarthAsset a = machine(AssetStatus.MAINTENANCE);
        granted.addAll(Set.of("EARTHMOVING_ADMIN", "EARTHMOVING_MAINTAIN", "EARTHMOVING_DEPLOY", "EARTHMOVING_WRITE"));
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Repair complete and tested"), actor));
        assertTrue(e.getMessage().contains("EARTHMOVING_RELEASE"));
        assertEquals(AssetStatus.MAINTENANCE, a.getStatus());
        verify(events, never()).save(any());
    }

    @Test
    void releaseIsRefusedWhileAWorkOrderThatBlocksReleaseIsOpen() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.addAll(Set.of("EARTHMOVING_RELEASE"));
        var wo = mock(za.co.handyflow.platform.earthmoving.domain.model.EarthWorkOrder.class);
        when(wo.getNumber()).thenReturn("WO-0007");
        when(workOrders.findOpenBlocking(tenant, a.getId())).thenReturn(List.of(wo));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Repair complete and tested"), actor));
        assertTrue(e.getMessage().contains("WO-0007"));
        assertEquals(AssetStatus.BREAKDOWN, a.getStatus());
        verify(events, never()).save(any());
    }

    @Test
    void aReleaseIsLinkedToTheLatestCompletedWorkOrder() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.addAll(Set.of("EARTHMOVING_RELEASE"));
        UUID woId = UUID.randomUUID();
        var wo = mock(za.co.handyflow.platform.earthmoving.domain.model.EarthWorkOrder.class);
        when(wo.getId()).thenReturn(woId);
        when(wo.getCompletedAt()).thenReturn(java.time.Instant.now());
        when(wo.getCompletedBy()).thenReturn(UUID.randomUUID());
        when(workOrders.findOpenBlocking(tenant, a.getId())).thenReturn(List.of());
        when(workOrders.findCompletedForAsset(eq(tenant), eq(a.getId()), any())).thenReturn(List.of(wo));
        service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Repair complete and tested"), actor);
        var saved = org.mockito.ArgumentCaptor.forClass(za.co.handyflow.platform.earthmoving.domain.model.EarthStatusEvent.class);
        verify(events).save(saved.capture());
        assertEquals(woId, saved.getValue().getWorkOrderId());
    }

    @Test
    void aStatusChangeThatIsNotAReleaseNeverLooksAtWorkOrders() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.addAll(Set.of("EARTHMOVING_MAINTAIN"));
        service.updateStatus(tenant, a.getId(), status("MAINTENANCE", "Scheduled 250h service"), actor);
        verify(workOrders, never()).findOpenBlocking(any(), any());
    }

    @Test
    void releaseWithNoCompletedWorkOrderIsRefusedUnlessOverriddenWithAReason() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.add("EARTHMOVING_RELEASE");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Looks fine to me now"), actor));
        assertTrue(e.getMessage().contains("No work order has been completed"));
        assertEquals(AssetStatus.BREAKDOWN, a.getStatus());
        verify(events, never()).save(any());

        service.updateStatus(tenant, a.getId(), new UpdateAssetStatusRequest("AVAILABLE", "Fault was a loose connector",
                "Supplier mechanic fixed it on site; no job card raised"), actor);
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
        var ev = org.mockito.ArgumentCaptor.forClass(EarthStatusEvent.class);
        verify(events).save(ev.capture());
        assertEquals("Supplier mechanic fixed it on site; no job card raised", ev.getValue().getOverrideReason());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_ASSET_RELEASE_OVERRIDDEN"), eq("EARTHMOVING_ASSET"), eq(a.getId()), any(), any());
    }

    @Test
    void releasingYourOwnCompletedWorkNeedsAnOverrideReason() {
        EarthAsset a = machine(AssetStatus.MAINTENANCE);
        granted.add("EARTHMOVING_RELEASE");
        evidenceFor(a, actor);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Service done and checked"), actor));
        assertTrue(e.getMessage().contains("different person"));
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(tenant, a.getId(),
                new UpdateAssetStatusRequest("AVAILABLE", "Service done and checked", "ok"), actor));
        service.updateStatus(tenant, a.getId(), new UpdateAssetStatusRequest("AVAILABLE", "Service done and checked",
                "Only one qualified person on site"), actor);
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
    }

    @Test
    void anOverrideReasonIsIgnoredAndNotRecordedWhenTheEvidenceIsThere() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.add("EARTHMOVING_RELEASE");
        evidenceFor(a, UUID.randomUUID());
        service.updateStatus(tenant, a.getId(), new UpdateAssetStatusRequest("AVAILABLE", "Repaired and tested", "not needed"), actor);
        var ev = org.mockito.ArgumentCaptor.forClass(EarthStatusEvent.class);
        verify(events).save(ev.capture());
        assertNull(ev.getValue().getOverrideReason());
        verify(trail, never()).record(any(), eq("EARTHMOVING_ASSET_RELEASE_OVERRIDDEN"), any(), any(), any(), any());
    }

    @Test
    void aReleaseFromBreakdownClosesTheBreakdownAlert() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.add("EARTHMOVING_RELEASE");
        evidenceFor(a, UUID.randomUUID());
        service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Repaired and tested"), actor);
        verify(alerts).resolveByKey(eq(tenant), eq("BREAKDOWN:" + a.getId()), eq(actor), any());
    }

    @Test
    void reportingABreakdownRaisesOneRoutedAlert() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        service.updateStatus(tenant, a.getId(), status("BREAKDOWN", "Hydraulic hose burst"), actor);
        verify(alerts).raise(eq(tenant), eq("BREAKDOWN"), eq("CRITICAL"), eq(a.getId()), any(), any(), any(), any(),
                eq("BREAKDOWN:" + a.getId()), any());
        verify(admins, never()).resolveTenantAdmins(any(TenantId.class));
    }

    @Test
    void anHourUpdateIsKeptInTheMeterHistory() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        BigDecimal before = a.getCurrentHours();
        service.updateHours(tenant, a.getId(), new UpdateHoursRequest(new BigDecimal("19000")), actor);
        verify(meters).record(eq(tenant), eq(a), eq(before), eq("MANUAL"), eq(actor));
    }

    @Test
    void aNewMachineCanBeRegisteredWithAKilometreMeter() {
        granted.add("EARTHMOVING_WRITE");
        var r = service.createAsset(tenant, new CreateAssetRequest("Tipper", "TRUCK", "T-01", null, null, null, null, null,
                "OWN", null, null, null, null, null, null, "kilometres"));
        assertEquals("KILOMETRES", r.meterType());
        assertEquals("km", r.meterUnit());
    }

    @Test
    void aMachineWithoutAMeterTypeCountsHours() {
        granted.add("EARTHMOVING_WRITE");
        var r = service.createAsset(tenant, new CreateAssetRequest("Dozer", "DOZER", "D-01", null, null, null, null, null,
                "OWN", null, null, null, null, null, null));
        assertEquals("HOURS", r.meterType());
        assertEquals("hrs", r.meterUnit());
    }

    @Test
    void aBreakdownAlsoTellsTheSiteSupervisor() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        UUID supervisor = UUID.randomUUID();
        when(sites.supervisorForAsset(tenant, a.getId())).thenReturn(java.util.Optional.of(supervisor));
        service.updateStatus(tenant, a.getId(), status("BREAKDOWN", "Hydraulic hose burst"), actor);
        verify(alerts).raise(eq(tenant), eq("BREAKDOWN"), any(), eq(a.getId()), any(), any(), any(), any(),
                eq("BREAKDOWN:" + a.getId()), eq(List.of(supervisor)));
    }

    @Test
    void releaseNeedsAWrittenReason() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.add("EARTHMOVING_RELEASE");
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(tenant, a.getId(), status("AVAILABLE", " "), actor));
        assertEquals(AssetStatus.BREAKDOWN, a.getStatus());
    }

    @Test
    void releaseIsRefusedWhileACriticalIncidentIsOpen() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        granted.add("EARTHMOVING_RELEASE");
        when(incidents.countOpenCritical(tenant, a.getId())).thenReturn(1L);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Repaired, tested under load"), actor));
        assertTrue(e.getMessage().contains("critical"));
        assertEquals(AssetStatus.BREAKDOWN, a.getStatus());
    }

    @Test
    void aReleaseRecordsWhoWhyAndTheMeterAndIsAuditedAsARelease() {
        EarthAsset a = machine(AssetStatus.BREAKDOWN);
        a.updateHours(new BigDecimal("812.0"));
        granted.add("EARTHMOVING_RELEASE");
        evidenceFor(a, UUID.randomUUID());
        service.updateStatus(tenant, a.getId(), status("AVAILABLE", "Pump replaced, tested under load"), actor);
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
        var ev = org.mockito.ArgumentCaptor.forClass(EarthStatusEvent.class);
        verify(events).save(ev.capture());
        assertEquals("Pump replaced, tested under load", ev.getValue().getReason());
        assertEquals(actor, ev.getValue().getActorUserId());
        assertEquals(new BigDecimal("812.0"), ev.getValue().getMeterHours());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_ASSET_RELEASED"), eq("EARTHMOVING_ASSET"), eq(a.getId()), any(), any());
    }

    @Test
    void theStatusEndpointNeverDeploysAndNeverLeavesADeploymentWithoutARecord() {
        EarthAsset a = machine(AssetStatus.DEPLOYED);
        a.returnToYard();
        granted.addAll(Set.of("EARTHMOVING_DEPLOY", "EARTHMOVING_WRITE"));
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(tenant, a.getId(), status("DEPLOYED", "to site"), actor));
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
    }

    @Test
    void returningADeployedMachineClosesItsDeploymentAndNeedsNoReason() {
        EarthAsset a = machine(AssetStatus.DEPLOYED);
        EarthDeployment d = mock(EarthDeployment.class);
        when(deployments.findOpenForAsset(tenant, a.getId())).thenReturn(Optional.of(d));
        granted.add("EARTHMOVING_DEPLOY");
        service.updateStatus(tenant, a.getId(), status("AVAILABLE", null), actor);
        verify(d).close("AVAILABLE");
        assertNull(a.getCurrentSite());
    }

    // ── deploy ───────────────────────────────────────────────────────────────

    @Test
    void deployingNeedsTheDeployAuthority() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        assertThrows(HandyFlowException.class, () -> service.deploy(tenant, a.getId(),
                new DeployAssetRequest("Mine A", "Anglo", null, null, null, null, null), actor));
    }

    @Test
    void anOpenCriticalIncidentBlocksDeployment() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_DEPLOY");
        when(incidents.countOpenCritical(tenant, a.getId())).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> service.deploy(tenant, a.getId(),
                new DeployAssetRequest("Mine A", "Anglo", null, null, null, null, null), actor));
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
        verify(deployments, never()).save(any());
    }

    @Test
    void deployWritesTheDeploymentAndTheStatusEvent() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_DEPLOY");
        service.deploy(tenant, a.getId(), new DeployAssetRequest(" Mine A ", "Anglo", null, null, null, null, null), actor);
        assertEquals("Mine A", a.getCurrentSite());
        verify(deployments).save(any(EarthDeployment.class));
        verify(events).save(any(EarthStatusEvent.class));
    }

    @Test
    void deployRecordsTheResolvedSiteCustomerAndProjectLinks() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_DEPLOY");
        UUID site = UUID.randomUUID(), customer = UUID.randomUUID(), project = UUID.randomUUID();
        when(sites.resolveForDeploy(any(), any())).thenReturn(new EarthSiteService.DeployTarget(site, "Mine A", customer, "Anglo American", project));
        service.deploy(tenant, a.getId(), new DeployAssetRequest("typed", "typed client", null, null, null, null, null, site, customer, project), actor);
        var captor = org.mockito.ArgumentCaptor.forClass(EarthDeployment.class);
        verify(deployments).save(captor.capture());
        assertEquals(site, captor.getValue().getSiteId());
        assertEquals(customer, captor.getValue().getCustomerId());
        assertEquals(project, captor.getValue().getProjectId());
        assertEquals("Mine A", captor.getValue().getSiteName());
        assertEquals("Anglo American", captor.getValue().getClientName());
        assertEquals("Mine A", a.getCurrentSite());
    }

    @Test
    void aRefusedSiteStopsTheDeploymentBeforeAnythingIsWritten() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_DEPLOY");
        when(sites.resolveForDeploy(any(), any())).thenThrow(new IllegalArgumentException("That site was not found."));
        assertThrows(IllegalArgumentException.class, () -> service.deploy(tenant, a.getId(),
                new DeployAssetRequest(null, null, null, null, null, null, null, UUID.randomUUID(), null, null), actor));
        verify(deployments, never()).save(any());
        verify(events, never()).save(any());
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
    }

    // ── tenants ──────────────────────────────────────────────────────────────

    @Test
    void anotherTenantsMachineIsNotFound() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        TenantId other = TenantId.generate();
        when(assets.findActiveById(other, a.getId())).thenReturn(Optional.empty());
        granted.addAll(Set.of("EARTHMOVING_WRITE", "EARTHMOVING_DEPLOY"));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(other, a.getId(), status("BREAKDOWN", "Hydraulic failure"), actor));
        assertThrows(ResourceNotFoundException.class, () -> service.updateHours(other, a.getId(), new UpdateHoursRequest(new BigDecimal("5")), actor));
        assertThrows(ResourceNotFoundException.class, () -> service.getStatusHistory(other, a.getId(), org.springframework.data.domain.Pageable.unpaged()));
    }

    // ── edit ─────────────────────────────────────────────────────────────────

    private UpdateAssetRequest edit(String fleet) {
        return new UpdateAssetRequest("CAT D9T", "DOZER", fleet, "Cat", "D9T", 2021, "S2", "GP2", "OWN", null, null, null,
                new BigDecimal("19000"), new BigDecimal("2300"), null, "fixed serial");
    }

    @Test
    void editingNeedsWriteAndAuditsBeforeAndAfter() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        assertThrows(HandyFlowException.class, () -> service.updateAsset(tenant, a.getId(), edit("D9-001")));
        granted.add("EARTHMOVING_WRITE");
        service.updateAsset(tenant, a.getId(), edit("D9-001"));
        assertEquals("S2", a.getSerialNumber());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_ASSET_UPDATED"), eq("EARTHMOVING_ASSET"), eq(a.getId()), any(), any());
    }

    @Test
    void editingToAnotherMachinesFleetNumberIsRefused() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        when(assets.existsActiveByFleetNumberExcluding(tenant, "D9-002", a.getId())).thenReturn(true);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.updateAsset(tenant, a.getId(), edit("D9-002")));
        assertTrue(e.getMessage().contains("D9-002"));
        assertEquals("D9-001", a.getFleetNumber());
    }

    @Test
    void aRaceOnTheFleetNumberComesBackAsAClearMessageNotAServerError() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        when(assets.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_earthmoving_assets_tenant_fleet_number"));
        assertThrows(IllegalArgumentException.class, () -> service.updateAsset(tenant, a.getId(), edit("D9-009")));
    }

    // ── maintenance ──────────────────────────────────────────────────────────

    @Test
    void recordingAServiceDoesNotReleaseTheMachine() {
        EarthAsset a = machine(AssetStatus.MAINTENANCE);
        granted.add("EARTHMOVING_MAINTAIN");
        service.recordMaintenance(tenant, a.getId(), new CreateMaintenanceRequest("service", "250h service", Instant.now(),
                new BigDecimal("260"), new BigDecimal("4500"), "Barloworld", "INV1"));
        assertEquals(AssetStatus.MAINTENANCE, a.getStatus());
        assertEquals(new BigDecimal("260"), a.getLastServiceHours());
        assertEquals(new BigDecimal("260"), a.getCurrentHours());   // the meter was behind the service reading
    }

    @Test
    void aMaintenanceTypeTheScreenOffersIsAccepted() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_MAINTAIN");
        assertDoesNotThrow(() -> service.recordMaintenance(tenant, a.getId(), new CreateMaintenanceRequest("HYDRAULICS",
                "Hose replaced", Instant.now(), null, null, null, null)));
    }

    // ── shifts ───────────────────────────────────────────────────────────────

    @Test
    void aSecondShiftIsRefusedEvenWhenTheDatabaseIndexCatchesTheRace() {
        EarthAsset a = machine(AssetStatus.AVAILABLE);
        granted.add("EARTHMOVING_WRITE");
        when(logs.findOpenLogForAsset(a.getId())).thenReturn(Optional.empty());
        when(logs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_em_oplogs_one_open_per_asset"));
        assertThrows(IllegalStateException.class, () -> service.startOperatorLog(tenant, a.getId(),
                new CreateOperatorLogRequest(null, "Sipho", "Mine A", Instant.now(), null)));
    }

    @Test
    void aShiftCannotStartOnAMachineUnderRepair() {
        EarthAsset a = machine(AssetStatus.MAINTENANCE);
        granted.add("EARTHMOVING_WRITE");
        assertThrows(IllegalStateException.class, () -> service.startOperatorLog(tenant, a.getId(),
                new CreateOperatorLogRequest(null, "Sipho", "Mine A", Instant.now(), null)));
    }

    @Test
    void completingAShiftRaisesTheMachineHoursAndNeverLowersThem() {
        EarthAsset a = machine(AssetStatus.DEPLOYED);
        a.updateHours(new BigDecimal("100"));
        granted.add("EARTHMOVING_WRITE");
        Instant t = Instant.parse("2026-10-15T06:00:00Z");
        OperatorLog log = OperatorLog.create(tenant, a.getId(), null, "Sipho", "Mine A", t, new BigDecimal("100"));
        when(logs.findByIdAndAssetId(any(), eq(a.getId()))).thenReturn(Optional.of(log));
        service.completeOperatorLog(tenant, a.getId(), UUID.randomUUID(),
                new CompleteOperatorLogRequest(t.plusSeconds(36000), new BigDecimal("108.5"), new BigDecimal("140"), null));
        assertEquals(new BigDecimal("108.5"), a.getCurrentHours());
    }

    @Test
    void anAssetCanOnlyBeCreatedWithAllowedValues() {
        granted.add("EARTHMOVING_WRITE");
        assertThrows(IllegalArgumentException.class, () -> service.createAsset(tenant, new CreateAssetRequest("X", "SPACESHIP",
                null, null, null, null, null, null, "OWN", null, null, null, null, null, null)));
        assertThrows(HandyFlowException.class, () -> { granted.clear(); service.createAsset(tenant, new CreateAssetRequest("X", "DOZER",
                null, null, null, null, null, null, "OWN", null, null, null, null, null, null)); });
    }
}
