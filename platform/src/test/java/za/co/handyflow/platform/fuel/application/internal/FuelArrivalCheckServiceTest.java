package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheck;
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheckRelease;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ReleaseRequest;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryCompartment;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Severity;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
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
class FuelArrivalCheckServiceTest {

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankerCompartmentRepository compartments;
    @Mock FuelDeliveryCompartmentRepository loadSplits;
    @Mock FuelArrivalCheckRepository checks;
    @Mock FuelEligibilityService eligibility;
    @Mock HrFacade hr;
    @Mock FuelArrivalCheckReleaseRepository releases;
    @Mock FuelSettingsService settings;
    @Mock FuelComplianceService compliance;
    @Mock AuditTrailFacade audit;
    @Mock FuelPushEventService pushEvents;
    @Mock FuelAlertRecipientService alertRecipients;
    @Mock FuelEnforcementService enforcement;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    FuelArrivalCheckService service;
    EmployeeSummary me;
    FuelDelivery load;
    FuelTanker tanker;

    @BeforeEach
    void setUp() {
        service = new FuelArrivalCheckService(deliveries, tankers, compartments, loadSplits, checks, eligibility, hr, new ObjectMapper(), releases, settings, compliance, audit, pushEvents, alertRecipients, enforcement);
        me = new EmployeeSummary(UUID.randomUUID(), "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE");
        tanker = FuelTanker.create(T, "CA 123-456", "Tanker 1");
        load = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", new BigDecimal("1000"), new BigDecimal("20"), Instant.now(), null, null);
        load.allocate(tanker.getId(), "CA 123-456", me.id(), "Moses Sithole");
        when(hr.findEmployeeByUserId(T, USER)).thenReturn(Optional.of(me));
        when(deliveries.findActiveById(eq(T), any())).thenReturn(Optional.of(load));
        when(tankers.findActiveById(eq(T), any())).thenReturn(Optional.of(tanker));
        when(loadSplits.findByDelivery(eq(T), any())).thenReturn(List.of(FuelDeliveryCompartment.create(T, load.getId(), UUID.randomUUID(), new BigDecimal("1000"))));
        when(checks.save(any())).thenAnswer(i -> i.getArgument(0));
        when(releases.save(any())).thenAnswer(i -> i.getArgument(0));
        when(releases.forCheck(eq(T), any(), any())).thenReturn(List.of());
        policy(null, true);
    }

    private FuelSettings policy(Integer hours, boolean enforced) {
        FuelSettings s = FuelSettings.forTenant(T);
        s.updateArrivalCheckPolicy(hours, enforced);
        when(settings.current(T)).thenReturn(s);
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(enforced ? FuelEnforcementService.Mode.ENFORCE : FuelEnforcementService.Mode.OFF);
        return s;
    }

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    /** Stores a check made {@code ageMinutes} before NOW by someone else and makes it the latest. */
    private FuelArrivalCheck storedCheck(boolean allowed, long ageMinutes, UUID by) {
        FuelArrivalCheck c = FuelArrivalCheck.record(T, load.getId(), by, allowed, "[]");
        org.springframework.test.util.ReflectionTestUtils.setField(c, "checkedAt", NOW.minusSeconds(ageMinutes * 60));
        when(checks.latest(eq(T), eq(load.getId()), any())).thenReturn(List.of(c));
        return c;
    }

    private static final UUID SUPERVISOR = UUID.randomUUID();
    private static ReleaseRequest rel() { return new ReleaseRequest("SITE_QUEUE", "Truck waited in the site queue after the check."); }

    private void result(Finding... f) {
        when(eligibility.evaluate(eq(T), any(), any(), any(), any(), any(), any())).thenReturn(new EligibilityResult(List.of(f)));
    }

    @Test @DisplayName("a clean check is allowed, and the result and time are recorded")
    void cleanCheckIsRecorded() {
        result();
        ArrivalCheckView v = service.check(T, USER, load.getId());
        assertTrue(v.allowed());
        assertNotNull(v.checkedAt());
        ArgumentCaptor<FuelArrivalCheck> saved = ArgumentCaptor.forClass(FuelArrivalCheck.class);
        verify(checks).save(saved.capture());
        assertEquals(USER, saved.getValue().getCheckedBy());
        assertTrue(saved.getValue().isAllowed());
    }

    @Test @DisplayName("a blocking finding is not allowed, and is shown as found")
    void blockedCheck() {
        result(new Finding("TANKER_NOT_APPROVED", Severity.BLOCK, "Tanker is not approved at this site."));
        ArrivalCheckView v = service.check(T, USER, load.getId());
        assertFalse(v.allowed());
        assertEquals("TANKER_NOT_APPROVED", v.findings().get(0).code());
    }

    @Test @DisplayName("a review point alone does not block the driver")
    void reviewDoesNotBlock() {
        result(new Finding("CLEANING_AGE", Severity.REVIEW, "Cleaning certificate is old."));
        assertTrue(service.check(T, USER, load.getId()).allowed());
    }

    @Test @DisplayName("another driver's load is reported as not found")
    void notYourLoad() {
        load.allocate(tanker.getId(), "CA 123-456", UUID.randomUUID(), "Someone Else");
        assertThrows(ResourceNotFoundException.class, () -> service.check(T, USER, load.getId()));
        verify(checks, never()).save(any());
    }

    @Test @DisplayName("a login with no employee record gets a clear message")
    void noEmployee() {
        when(hr.findEmployeeByUserId(T, USER)).thenReturn(Optional.empty());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.check(T, USER, load.getId()));
        assertTrue(e.getMessage().contains("not linked"));
    }

    @Test @DisplayName("the latest check comes back with its findings")
    void latest() {
        result(new Finding("X", Severity.INFO, "Note"));
        service.check(T, USER, load.getId());
        ArgumentCaptor<FuelArrivalCheck> saved = ArgumentCaptor.forClass(FuelArrivalCheck.class);
        verify(checks).save(saved.capture());
        when(checks.latest(eq(T), any(), any())).thenReturn(List.of(saved.getValue()));
        ArrivalCheckView v = service.latest(T, USER, load.getId()).orElseThrow();
        assertEquals("X", v.findings().get(0).code());
        when(checks.latest(eq(T), any(), any())).thenReturn(List.of());
        assertTrue(service.latest(T, USER, load.getId()).isEmpty());
    }

    // ---- Batch A: freshness, release and server enforcement ----

    @Test @DisplayName("the boundary: exactly 12 hours old is fresh, one minute older is stale")
    void twelveHourBoundary() {
        Instant checked = NOW.minusSeconds(12 * 3600);
        assertEquals(FuelArrivalCheckPolicy.Status.FRESH, FuelArrivalCheckPolicy.status(checked, true, null, 12, NOW));
        assertEquals(FuelArrivalCheckPolicy.Status.STALE, FuelArrivalCheckPolicy.status(checked.minusSeconds(1), true, null, 12, NOW));
        assertEquals(FuelArrivalCheckPolicy.Status.MISSING, FuelArrivalCheckPolicy.status(null, false, null, 12, NOW));
        assertEquals(FuelArrivalCheckPolicy.Status.BLOCKED, FuelArrivalCheckPolicy.status(NOW, false, null, 12, NOW));
    }

    @Test @DisplayName("the company's own window replaces the 12 hour default")
    void companyWindow() {
        policy(4, true);
        storedCheck(true, 5 * 60, USER);
        assertEquals("STALE", service.status(T, load.getId(), NOW).status());
        assertEquals(4, service.status(T, load.getId(), NOW).freshHours());
        policy(null, true);
        assertEquals("FRESH", service.status(T, load.getId(), NOW).status());
        assertEquals(12, service.status(T, load.getId(), NOW).freshHours());
    }

    @Test @DisplayName("a company cannot set a window outside 1 to 72 hours")
    void windowBounds() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertThrows(IllegalArgumentException.class, () -> s.updateArrivalCheckPolicy(0, true));
        assertThrows(IllegalArgumentException.class, () -> s.updateArrivalCheckPolicy(73, true));
        s.updateArrivalCheckPolicy(72, false);
        assertEquals(72, s.getArrivalCheckFreshHours());
    }

    @Test @DisplayName("a stale check is released with a reason; the original check is untouched and an audit event is written")
    void releaseStale() {
        FuelArrivalCheck original = storedCheck(true, 13 * 60, USER);
        result();
        ArrivalCheckView v = service.release(T, SUPERVISOR, load.getId(), rel(), NOW);
        assertEquals("RELEASED", v.status());
        assertEquals(original.getId(), v.checkId());
        assertEquals(original.getCheckedAt(), v.checkedAt());
        ArgumentCaptor<FuelArrivalCheckRelease> saved = ArgumentCaptor.forClass(FuelArrivalCheckRelease.class);
        verify(releases).save(saved.capture());
        assertEquals(SUPERVISOR, saved.getValue().getReleasedBy());
        assertEquals(original.getId(), saved.getValue().getCheckId());
        assertEquals("SITE_QUEUE", saved.getValue().getReasonCode());
        verify(audit).record(eq(T), eq("FUEL_ARRIVAL_CHECK_RELEASED"), eq("FuelArrivalCheck"), eq(original.getId()), any(), any());
        verify(checks, never()).save(any());
    }

    @Test @DisplayName("a release stops counting after the window, and the check is stale again")
    void releaseExpires() {
        storedCheck(true, 40 * 60, USER);
        FuelArrivalCheckRelease r = FuelArrivalCheckRelease.record(T, UUID.randomUUID(), load.getId(), "SITE_QUEUE", "Waited in the queue.", SUPERVISOR, NOW.minusSeconds(13 * 3600));
        when(releases.forCheck(eq(T), any(), any())).thenReturn(List.of(r));
        assertEquals("STALE", service.status(T, load.getId(), NOW).status());
    }

    @Test @DisplayName("a release needs a known reason code and a real explanation")
    void releaseNeedsReason() {
        storedCheck(true, 13 * 60, USER);
        result();
        assertThrows(IllegalArgumentException.class, () -> service.release(T, SUPERVISOR, load.getId(), new ReleaseRequest("WHATEVER", "A long enough explanation."), NOW));
        assertThrows(IllegalArgumentException.class, () -> service.release(T, SUPERVISOR, load.getId(), new ReleaseRequest("SITE_QUEUE", "short"), NOW));
        verify(releases, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("a blocked check cannot be released, and neither can a missing or still fresh one")
    void onlyStaleIsReleasable() {
        storedCheck(false, 13 * 60, USER);
        result();
        assertThrows(IllegalStateException.class, () -> service.release(T, SUPERVISOR, load.getId(), rel(), NOW));
        storedCheck(true, 60, USER);
        assertThrows(IllegalStateException.class, () -> service.release(T, SUPERVISOR, load.getId(), rel(), NOW));
        when(checks.latest(eq(T), eq(load.getId()), any())).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.release(T, SUPERVISOR, load.getId(), rel(), NOW));
        verify(releases, never()).save(any());
    }

    @Test @DisplayName("safety-critical: a stale check is not released when the live rules now block it")
    void safetyCriticalNotReleased() {
        storedCheck(true, 13 * 60, USER);
        result(new Finding("COMPARTMENT_PRODUCT_NOT_RATED", Severity.BLOCK, "Compartment 2 is not rated for this product."));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.release(T, SUPERVISOR, load.getId(), rel(), NOW));
        assertTrue(e.getMessage().contains("no longer passes"));
        verify(releases, never()).save(any());
    }

    @Test @DisplayName("safety-critical: an expired statutory licence stops a release")
    void expiredLicenceNotReleased() {
        storedCheck(true, 13 * 60, USER);
        result();
        doThrow(new IllegalStateException("Cannot allocate: the company has an expired Dangerous Goods licence.")).when(compliance)
                .requireCompliantForAllocation(any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.release(T, SUPERVISOR, load.getId(), rel(), NOW));
        verify(releases, never()).save(any());
    }

    @Test @DisplayName("the person who made the check cannot release it")
    void noSelfRelease() {
        storedCheck(true, 13 * 60, SUPERVISOR);
        result();
        assertThrows(IllegalStateException.class, () -> service.release(T, SUPERVISOR, load.getId(), rel(), NOW));
        verify(releases, never()).save(any());
    }

    @Test @DisplayName("tenant isolation: another company's load is not found, and nothing is released")
    void releaseTenantIsolation() {
        TenantId other = TenantId.of(UUID.randomUUID());
        when(deliveries.findActiveById(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.release(other, SUPERVISOR, load.getId(), rel(), NOW));
        verify(releases, never()).save(any());
    }

    @Test @DisplayName("enforcement: missing, stale and blocked checks stop the step; fresh and released checks let it through")
    void gate() {
        result();
        when(checks.latest(eq(T), eq(load.getId()), any())).thenReturn(List.of());
        assertTrue(assertThrows(IllegalStateException.class, () -> service.requireClear(T, load.getId(), NOW)).getMessage().contains("No check"));
        storedCheck(true, 13 * 60, USER);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.requireClear(T, load.getId(), NOW)).getMessage().contains("out of date"));
        storedCheck(false, 10, USER);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.requireClear(T, load.getId(), NOW)).getMessage().contains("blocks"));
        storedCheck(true, 10, USER);
        assertDoesNotThrow(() -> service.requireClear(T, load.getId(), NOW));
        FuelArrivalCheck old = storedCheck(true, 13 * 60, USER);
        when(releases.forCheck(eq(T), any(), any())).thenReturn(List.of(FuelArrivalCheckRelease.record(T, old.getId(), load.getId(), "SITE_QUEUE", "Waited in the queue.", SUPERVISOR, NOW.minusSeconds(600))));
        assertDoesNotThrow(() -> service.requireClear(T, load.getId(), NOW));
    }

    @Test @DisplayName("enforcement revalidates: a fresh check does not pass when the live rules now block")
    void gateRevalidates() {
        storedCheck(true, 10, USER);
        result(new Finding("SITE_DRIVER_SUSPENDED", Severity.BLOCK, "The driver is suspended at this site."));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.requireClear(T, load.getId(), NOW)).getMessage().contains("no longer passes"));
    }

    @Test @DisplayName("enforcement is off by default, so existing companies and the web driver screen are unchanged")
    void gateOffByDefault() {
        policy(null, false);
        when(checks.latest(eq(T), eq(load.getId()), any())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.requireClear(T, load.getId(), NOW));
    }

    // ---- Batch C: blocked check alerts ----

    @Test @DisplayName("a blocked check tells the driver, and the chosen operations people but not the driver twice")
    void blockedTellsDriverAndOps() {
        UUID ops = UUID.randomUUID();
        when(alertRecipients.recipientsFor(T, FuelAlertRecipientService.ARRIVAL_BLOCKED)).thenReturn(List.of(
                new za.co.handyflow.platform.notifications.application.Recipient(ops, "ops@x.co.za", "Ops", null),
                new za.co.handyflow.platform.notifications.application.Recipient(USER, "driver@x.co.za", "Driver", null)));
        result(new Finding("SITE_DRIVER_SUSPENDED", Severity.BLOCK, "Suspended at this site."));
        service.check(T, USER, load.getId());
        ArgumentCaptor<java.util.Collection<UUID>> to = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(pushEvents, times(2)).publish(eq(T), eq(FuelPushEventService.ARRIVAL_CHECK_BLOCKED), any(), to.capture(), any(), any(), any());
        assertEquals(List.of(USER), List.copyOf(to.getAllValues().get(0)));
        assertEquals(List.of(ops), List.copyOf(to.getAllValues().get(1)));
    }

    @Test @DisplayName("the event key is the load and the reasons, so the same blocked reasons are told once")
    void blockedDedupKey() {
        when(alertRecipients.recipientsFor(eq(T), any())).thenReturn(List.of());
        result(new Finding("B", Severity.BLOCK, "b"), new Finding("A", Severity.BLOCK, "a"));
        service.check(T, USER, load.getId());
        service.check(T, USER, load.getId());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(pushEvents, atLeast(2)).publish(eq(T), any(), key.capture(), any(), any(), any(), any());
        assertEquals(1, key.getAllValues().stream().distinct().count());
        assertTrue(key.getValue().endsWith(":A,B"));
    }

    @Test @DisplayName("an allowed check sends nothing, and a failing alert never fails the check")
    void allowedNoAlertAndAlertsNeverFail() {
        result();
        service.check(T, USER, load.getId());
        verifyNoInteractions(pushEvents);
        when(alertRecipients.recipientsFor(eq(T), any())).thenThrow(new RuntimeException("down"));
        result(new Finding("X", Severity.BLOCK, "x"));
        assertFalse(service.check(T, USER, load.getId()).allowed());
    }

    @Test @DisplayName("watching only (monitor, or a pilot outside its depots): a missing check is recorded, not refused")
    void monitorRecordsInsteadOfRefusing() {
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.MONITOR);
        when(checks.latest(eq(T), eq(load.getId()), any())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.requireClear(T, load.getId(), NOW));
        verify(enforcement).observe(eq(T), eq("ARRIVAL_CHECK"), eq(load.getId()), contains("No check"), eq(NOW));
    }

    @Test @DisplayName("a safety-critical failure is never waived by a watch-only stage")
    void monitorStillStopsSafetyCritical() {
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.MONITOR);
        storedCheck(true, 30, UUID.randomUUID());
        result(new Finding("SITE_DRIVER_SUSPENDED", Severity.BLOCK, "The driver is suspended at this site."));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.requireClear(T, load.getId(), NOW)).getMessage().contains("no longer passes"));
    }

    @Test @DisplayName("off: nothing is asked and nothing recorded")
    void offAsksNothing() {
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.OFF);
        assertDoesNotThrow(() -> service.requireClear(T, load.getId(), NOW));
        verify(enforcement, never()).observe(any(), any(), any(), any(), any());
    }

    @Test @DisplayName("dispatch: a load whose live rules block cannot leave once the rollout has started")
    void dispatchBlockedByLiveRules() {
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.MONITOR);
        result(new Finding("SITE_DRIVER_SUSPENDED", Severity.BLOCK, "The driver is suspended at this site."));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.requireEligibleToDispatch(T, load.getId())).getMessage().contains("cannot leave"));
    }

    @Test @DisplayName("dispatch: nothing is asked while the control is OFF, and a load with no tanker is left alone")
    void dispatchOffOrNoTanker() {
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.OFF);
        result(new Finding("SITE_DRIVER_SUSPENDED", Severity.BLOCK, "The driver is suspended at this site."));
        assertDoesNotThrow(() -> service.requireEligibleToDispatch(T, load.getId()));
        FuelDelivery untankered = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", new BigDecimal("1000"), new BigDecimal("20"), Instant.now(), null, null);
        when(deliveries.findActiveById(eq(T), eq(untankered.getId()))).thenReturn(Optional.of(untankered));
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.ENFORCE);
        assertDoesNotThrow(() -> service.requireEligibleToDispatch(T, untankered.getId()));
    }

    @Test @DisplayName("dispatch: an eligible load leaves")
    void dispatchEligible() {
        when(enforcement.mode(eq(T), eq("ARRIVAL_CHECK"), any())).thenReturn(FuelEnforcementService.Mode.ENFORCE);
        result();
        assertDoesNotThrow(() -> service.requireEligibleToDispatch(T, load.getId()));
        verify(compliance).requireCompliantForAllocation(eq(T), any(), any(), any(), any());
    }
}
