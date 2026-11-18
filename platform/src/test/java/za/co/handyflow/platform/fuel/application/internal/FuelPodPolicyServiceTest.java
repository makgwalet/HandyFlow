package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.CompleteDeliveryRequest;
import za.co.handyflow.platform.fuel.dto.PodDtos.ApproveRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPodPolicyServiceTest {

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerSiteRepository sites;
    @Mock FuelDeliveryEvidenceRepository evidence;
    @Mock FuelPodExceptionRepository exceptions;
    @Mock FuelSettingsService settings;
    @Mock FuelService fuelService;
    @Mock FuelArrivalCheckService arrivalChecks;
    @Mock FuelEnforcementService enforcement;
    @Mock AuditTrailFacade audit;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID DRIVER = UUID.randomUUID();
    static final UUID BOSS = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    FuelPodPolicyService service;
    FuelDelivery load;
    FuelSettings cfg;
    FuelCustomerSite site;

    @BeforeEach
    void setUp() {
        service = new FuelPodPolicyService(deliveries, sites, evidence, exceptions, settings, fuelService, arrivalChecks, enforcement, audit);
        site = FuelCustomerSite.create(T, UUID.randomUUID(), "Mine gate 3", null, null, null, null, null);
        load = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", new BigDecimal("1000"), new BigDecimal("20"), Instant.now(), null, null);
        load.assignSite(site.getId());
        cfg = FuelSettings.forTenant(T);
        cfg.updatePodPolicy(true, List.of("UNATTENDED_SITE", "AFTER_HOURS_APPROVED", "AUTOMATED_RECEIVING", "CONTACTLESS_AUTHORISED"));
        when(settings.current(T)).thenReturn(cfg);
        when(enforcement.mode(eq(T), eq("POD_POLICY"), any())).thenAnswer(i -> cfg.isPodPolicyEnforced() ? FuelEnforcementService.Mode.ENFORCE : FuelEnforcementService.Mode.OFF);
        when(deliveries.findActiveById(eq(T), any())).thenReturn(Optional.of(load));
        when(sites.findActiveById(eq(T), any())).thenReturn(Optional.of(site));
        when(exceptions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(exceptions.forDelivery(eq(T), any())).thenReturn(List.of());
        when(evidence.findByDelivery(eq(T), any())).thenReturn(List.of());
    }

    private static CompleteDeliveryRequest req(String receiver, String exType, String exReason) {
        // An ordinary delivery arrives signed; an exception has nobody to sign.
        return new CompleteDeliveryRequest(new BigDecimal("1000"), receiver, null, null, null, false, null, exType == null ? SIGNATURE : null, UUID.randomUUID(), exType, exReason);
    }

    static final String SIGNATURE = "data:image/png;base64,iVBORw0KGgo=";

    private static CompleteDeliveryRequest unsigned(String receiver) {
        return new CompleteDeliveryRequest(new BigDecimal("1000"), receiver, null, null, null, false, null, null, UUID.randomUUID(), null, null);
    }

    private void photo() {
        when(evidence.findByDelivery(eq(T), any())).thenReturn(List.of(FuelDeliveryEvidence.create(T, load.getId(), "PHOTO", "x.jpg", null, null, null, "gate", DRIVER)));
    }

    @Test @DisplayName("a typed name is not a signature: an attended delivery without one is refused before anything is changed")
    void nameIsNotASignature() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), unsigned("Johannes van Wyk"), NOW));
        assertTrue(e.getMessage().contains("signature"));
        verify(fuelService, never()).completeDelivery(any(), any(), any());
    }

    @Test @DisplayName("a signature already uploaded on the load counts, so the office can complete after the driver captured it")
    void uploadedSignatureCounts() {
        when(evidence.findByDelivery(eq(T), any())).thenReturn(List.of(FuelDeliveryEvidence.create(T, load.getId(), "SIGNATURE", "sig.png", null, null, null, "receiver", DRIVER)));
        service.complete(T, DRIVER, load.getId(), unsigned("Johannes van Wyk"), NOW);
        verify(fuelService).completeDelivery(eq(T), eq(load.getId()), any());
    }

    @Test @DisplayName("a photo is not a signature")
    void photoIsNotASignature() {
        photo();
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), unsigned("Johannes van Wyk"), NOW));
    }

    @Test @DisplayName("a refused arrival check stops completion, for the office as for the driver, before anything is changed")
    void arrivalCheckGate() {
        doThrow(new IllegalStateException("The arrival check found something that blocks this delivery.")).when(arrivalChecks).requireClear(T, load.getId());
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req("Johannes van Wyk", null, null), NOW));
        verify(fuelService, never()).completeDelivery(any(), any(), any());
        verify(exceptions, never()).save(any());
    }

    @Test @DisplayName("an ordinary delivery with a receiver name goes through, with no exception recorded")
    void ordinary() {
        service.complete(T, DRIVER, load.getId(), req("Johannes van Wyk", null, null), NOW);
        verify(fuelService).completeDelivery(eq(T), eq(load.getId()), any());
        verify(exceptions, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("missing receiver details stop an ordinary delivery before anything is changed")
    void missingReceiver() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req("  ", null, null), NOW));
        assertTrue(e.getMessage().contains("name of the person who received"));
        verify(fuelService, never()).completeDelivery(any(), any(), any());
    }

    @Test @DisplayName("signing on behalf needs the name of the designated receiver")
    void onBehalfNeedsName() {
        CompleteDeliveryRequest r = new CompleteDeliveryRequest(new BigDecimal("1000"), "Guard", null, null, null, true, " ", null, UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), r, NOW));
        verify(fuelService, never()).completeDelivery(any(), any(), any());
    }

    @Test @DisplayName("an unattended site, with the reason and evidence, is accepted and audited")
    void unattendedSite() {
        site.setPodExceptionType("UNATTENDED_SITE");
        photo();
        service.complete(T, DRIVER, load.getId(), req(null, "UNATTENDED_SITE", "Gate locked, tank filled and photographed."), NOW);
        ArgumentCaptor<FuelPodException> saved = ArgumentCaptor.forClass(FuelPodException.class);
        verify(exceptions).save(saved.capture());
        assertEquals("USED", saved.getValue().getStatus());
        assertEquals(1, saved.getValue().getEvidenceCount());
        verify(audit).record(eq(T), eq("FUEL_POD_EXCEPTION_USED"), eq("FuelDelivery"), eq(load.getId()), any(), any());
    }

    @Test @DisplayName("an exception is refused without evidence, without a reason, or for a site that is not marked")
    void exceptionValidation() {
        site.setPodExceptionType("UNATTENDED_SITE");
        assertTrue(assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "UNATTENDED_SITE", "Gate locked, tank filled."), NOW)).getMessage().contains("evidence"));
        photo();
        assertTrue(assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "UNATTENDED_SITE", "short"), NOW)).getMessage().contains("Explain"));
        site.setPodExceptionType(null);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "UNATTENDED_SITE", "Gate locked, tank filled."), NOW)).getMessage().contains("not marked"));
        site.setPodExceptionType("AUTOMATED_RECEIVING");
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "UNATTENDED_SITE", "Gate locked, tank filled."), NOW));
        verify(fuelService, never()).completeDelivery(any(), any(), any());
    }

    @Test @DisplayName("a type the company has not allowed cannot be used, and a receiver name cannot be mixed with an exception")
    void notAllowedOrMixed() {
        site.setPodExceptionType("UNATTENDED_SITE");
        photo();
        cfg.updatePodPolicy(true, List.of());
        assertTrue(assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "UNATTENDED_SITE", "Gate locked, tank filled."), NOW)).getMessage().contains("does not allow"));
        cfg.updatePodPolicy(true, List.of("UNATTENDED_SITE"));
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req("Someone", "UNATTENDED_SITE", "Gate locked, tank filled."), NOW));
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "MADE_UP", "Gate locked, tank filled."), NOW));
    }

    @Test @DisplayName("an after-hours delivery needs an approval first; the driver cannot grant it, and an approved one is used once")
    void afterHoursNeedsApproval() {
        photo();
        assertTrue(assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "AFTER_HOURS_APPROVED", "Arrived 22:00, site closed, agreed with the office."), NOW)).getMessage().contains("approval"));
        verify(fuelService, never()).completeDelivery(any(), any(), any());

        FuelPodException approved = FuelPodException.approved(T, load.getId(), "AFTER_HOURS_APPROVED", "Office agreed an after-hours drop.", BOSS, NOW);
        when(exceptions.forDelivery(eq(T), any())).thenReturn(List.of(approved));
        service.complete(T, DRIVER, load.getId(), req(null, "AFTER_HOURS_APPROVED", "Arrived 22:00, site closed, agreed with the office."), NOW);
        assertEquals("USED", approved.getStatus());
        assertEquals(BOSS, approved.getApprovedBy());
        verify(fuelService).completeDelivery(eq(T), eq(load.getId()), any());
    }

    @Test @DisplayName("an approval of the wrong type does not cover a contactless delivery")
    void wrongTypeApproval() {
        photo();
        when(exceptions.forDelivery(eq(T), any())).thenReturn(List.of(FuelPodException.approved(T, load.getId(), "AFTER_HOURS_APPROVED", "Office agreed an after-hours drop.", BOSS, NOW)));
        assertThrows(IllegalStateException.class, () -> service.complete(T, DRIVER, load.getId(), req(null, "CONTACTLESS_AUTHORISED", "Customer asked for contactless drop."), NOW));
    }

    @Test @DisplayName("approving records the approver, the reason and an audit event; site types cannot be approved this way")
    void approve() {
        var v = service.approve(T, BOSS, load.getId(), new ApproveRequest("CONTACTLESS_AUTHORISED", "Customer asked for a contactless drop."), NOW);
        assertEquals("APPROVED", v.status());
        assertEquals(BOSS, v.approvedBy());
        verify(audit).record(eq(T), eq("FUEL_POD_EXCEPTION_APPROVED"), eq("FuelDelivery"), eq(load.getId()), any(), any());
        assertThrows(IllegalArgumentException.class, () -> service.approve(T, BOSS, load.getId(), new ApproveRequest("UNATTENDED_SITE", "Customer asked for a contactless drop."), NOW));
        assertThrows(IllegalArgumentException.class, () -> service.approve(T, BOSS, load.getId(), new ApproveRequest("CONTACTLESS_AUTHORISED", "short"), NOW));
    }

    @Test @DisplayName("tenant isolation: another company's load is not found and nothing is approved")
    void tenantIsolation() {
        TenantId other = TenantId.of(UUID.randomUUID());
        when(deliveries.findActiveById(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.approve(other, BOSS, load.getId(), new ApproveRequest("CONTACTLESS_AUTHORISED", "Customer asked for a contactless drop."), NOW));
        assertThrows(ResourceNotFoundException.class, () -> service.complete(other, DRIVER, load.getId(), req("X", null, null), NOW));
        verify(exceptions, never()).save(any());
    }

    @Test @DisplayName("off by default: nothing is checked, so existing companies and the web screen are unchanged")
    void offByDefault() {
        cfg.updatePodPolicy(false, List.of());
        service.complete(T, DRIVER, load.getId(), req(null, null, null), NOW);
        verify(fuelService).completeDelivery(eq(T), eq(load.getId()), any());
    }

    @Test @DisplayName("a retried completion is returned as it was and records no second exception")
    void replay() {
        load.complete(new BigDecimal("1000"), null, null, null, null, false, null, null);
        UUID rid = UUID.randomUUID();
        load.recordCompletionRequest(rid);
        CompleteDeliveryRequest again = new CompleteDeliveryRequest(new BigDecimal("1000"), null, null, null, null, false, null, null, rid, "UNATTENDED_SITE", "Gate locked, tank filled.");
        service.complete(T, DRIVER, load.getId(), again, NOW);
        verify(fuelService).completeDelivery(eq(T), eq(load.getId()), any());
        verify(exceptions, never()).save(any());
    }

    @Test @DisplayName("a site can only be marked with the two site types")
    void siteDesignation() {
        assertThrows(IllegalArgumentException.class, () -> site.setPodExceptionType("AFTER_HOURS_APPROVED"));
        site.setPodExceptionType("AUTOMATED_RECEIVING");
        assertEquals("AUTOMATED_RECEIVING", site.getPodExceptionType());
        site.setPodExceptionType("");
        assertNull(site.getPodExceptionType());
    }

    @Test @DisplayName("the company cannot allow an unknown exception type")
    void policyTypes() {
        assertThrows(IllegalArgumentException.class, () -> cfg.updatePodPolicy(true, List.of("ANYTHING")));
    }

    @Test @DisplayName("watching only: an unsigned delivery is completed as asked and what would have been refused is recorded")
    void monitorRecordsInsteadOfRefusing() {
        when(enforcement.mode(eq(T), eq("POD_POLICY"), any())).thenReturn(FuelEnforcementService.Mode.MONITOR);
        service.complete(T, DRIVER, load.getId(), unsigned("Johannes van Wyk"), NOW);
        verify(fuelService).completeDelivery(eq(T), eq(load.getId()), any());
        verify(enforcement).observe(eq(T), eq("POD_POLICY"), eq(load.getId()), contains("signature"), eq(NOW));
        verify(exceptions, never()).save(any());
    }

    @Test @DisplayName("watching only: a properly signed delivery records nothing")
    void monitorQuietWhenFine() {
        when(enforcement.mode(eq(T), eq("POD_POLICY"), any())).thenReturn(FuelEnforcementService.Mode.MONITOR);
        service.complete(T, DRIVER, load.getId(), req("Johannes van Wyk", null, null), NOW);
        verify(enforcement, never()).observe(any(), any(), any(), any(), any());
    }
}
