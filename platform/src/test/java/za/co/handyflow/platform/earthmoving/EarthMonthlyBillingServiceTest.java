package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthMonthlyBillingServiceTest {
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    static final YearMonth SEPT = YearMonth.of(2026, 9);

    EarthDeploymentRepository deployments = mock(EarthDeploymentRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthHireChargeRepository charges = mock(EarthHireChargeRepository.class);
    EarthBillingRunRepository runs = mock(EarthBillingRunRepository.class);
    EarthSettingsRepository settings = mock(EarthSettingsRepository.class);
    EarthHireService hire = mock(EarthHireService.class);
    CrmFacade crm = mock(CrmFacade.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthMonthlyBillingService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    UUID customer = UUID.randomUUID();
    EarthAsset asset;
    EarthDeployment deployment;

    static BigDecimal n(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        service = new EarthMonthlyBillingService(deployments, assets, charges, runs, settings, hire, crm, access, trail);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null, "OWN", null, null, null, n("18500"), n("2200"), null);
        deployment = onRun(asset, "DAILY");
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
        when(deployments.findAutoBilled(eq(tenant), any(), any())).thenReturn(List.of(deployment));
        when(deployments.findByTenantAndId(tenant, deployment.getId())).thenReturn(Optional.of(deployment));
        when(crm.findCustomerById(eq(tenant), eq(customer))).thenReturn(Optional.of(new CustomerSummary(customer, "Anglo", null, null, null, null, null, null)));
        when(charges.findLiveOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        when(charges.findPendingStartingBetween(any(), any(), any())).thenReturn(List.of());
        when(runs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(runs.save(any())).thenAnswer(i -> i.getArgument(0));
        when(hire.quote(eq(tenant), any(), any())).thenAnswer(i -> quoteOf(i.getArgument(1)));
        when(hire.raise(eq(tenant), any(), any(), any())).thenAnswer(i -> quoteOf(i.getArgument(1)));
        granted.add("EARTHMOVING_ADMIN");
    }

    EarthDeployment onRun(EarthAsset a, String basis) {
        EarthDeployment d = EarthDeployment.create(tenant, a.getId(), "Mine A", "Anglo", null, null, null, null, null).withLinks(null, customer, null);
        ReflectionTestUtils.setField(d, "deployedAt", LocalDate.of(2026, 8, 1).atStartOfDay(ZONE).toInstant());
        d.setAutoBilling(basis);
        return d;
    }

    HireChargeResponse quoteOf(HireChargeRequest r) {
        BigDecimal qty = n("" + (r.periodTo().toEpochDay() - r.periodFrom().toEpochDay() + 1));
        BigDecimal amount = qty.multiply(n("18500"));
        return new HireChargeResponse(null, r.deploymentId(), asset.getId(), "D9-001 CAT D9", customer, "Anglo", "Mine A", r.periodFrom(), r.periodTo(),
                r.basis(), qty, n("18500"), n("15"), amount, amount.multiply(n("0.15")), amount.multiply(n("1.15")), "Hire", "PREVIEW",
                null, null, null, null, "ASSET", null, null, null);
    }
    EarthHireCharge pending(EarthDeployment d, String from, String to) {
        return EarthHireCharge.create(tenant, d.getId(), asset.getId(), customer, LocalDate.parse(from), LocalDate.parse(to), "DAILY", n("30"), n("18500"), null, "Hire", actor);
    }

    // ── looking ──────────────────────────────────────────────────────────────

    @Test void previewNeedsAdminAndAFinishedMonth() {
        granted.clear();
        granted.add("EARTHMOVING_READ");
        assertThrows(HandyFlowException.class, () -> service.preview(tenant, "2026-09"));
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.preview(tenant, "2026-10"));
        assertThrows(IllegalArgumentException.class, () -> service.preview(tenant, "soon"));
    }
    @Test void aDeploymentOnTheRunShowsAsReadyWithItsAmountForTheWholeMonth() {
        BillingRunResponse r = service.preview(tenant, SEPT, TODAY);
        assertFalse(r.applied());
        assertEquals(1, r.ready());
        BillingLine l = r.lines().get(0);
        assertEquals("READY", l.state());
        assertEquals(LocalDate.of(2026, 9, 1), l.periodFrom());
        assertEquals(LocalDate.of(2026, 9, 30), l.periodTo());
        assertEquals(n("30"), l.quantity());
        assertEquals("Anglo", l.customerName());
        verify(hire, never()).raise(any(), any(), any(), any());
    }
    @Test void aMonthThatIsAlreadyChargedIsSkippedNotRepeated() {
        when(charges.findLiveOverlapping(any(), any(), any(), any())).thenReturn(List.of(pending(deployment, "2026-09-01", "2026-09-30")));
        BillingRunResponse r = service.preview(tenant, SEPT, TODAY);
        assertEquals(1, r.alreadyBilled());
        assertEquals(0, r.ready());
    }
    @Test void aPartlyChargedMonthNeedsAPerson() {
        when(charges.findLiveOverlapping(any(), any(), any(), any())).thenReturn(List.of(pending(deployment, "2026-09-01", "2026-09-10")));
        BillingLine l = service.preview(tenant, SEPT, TODAY).lines().get(0);
        assertEquals("EXCEPTION", l.state());
        assertTrue(l.message().contains("Part of this month is already charged"));
    }
    @Test void whateverStopsAChargeIsShownAsTheReasonNotThrown() {
        when(hire.quote(eq(tenant), any(), any())).thenThrow(new IllegalArgumentException("Set the machine's daily rate first."));
        BillingRunResponse r = service.preview(tenant, SEPT, TODAY);
        assertEquals(1, r.exceptions());
        assertEquals("Set the machine's daily rate first.", r.lines().get(0).message());
    }
    @Test void aDeploymentNotOnHireInTheMonthIsLeftOut() {
        ReflectionTestUtils.setField(deployment, "deployedAt", LocalDate.of(2026, 10, 1).atStartOfDay(ZONE).toInstant());
        assertTrue(service.preview(tenant, SEPT, TODAY).lines().isEmpty());
    }
    @Test void aMachineThatLeftMidMonthIsBilledToThatDay() {
        ReflectionTestUtils.setField(deployment, "returnedAt", LocalDate.of(2026, 9, 20).atStartOfDay(ZONE).plusHours(10).toInstant());
        BillingLine l = service.preview(tenant, SEPT, TODAY).lines().get(0);
        assertEquals(LocalDate.of(2026, 9, 20), l.periodTo());
        assertEquals(n("20"), l.quantity());
    }

    // ── running ──────────────────────────────────────────────────────────────

    @Test void runningNeedsAdmin() {
        granted.clear();
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(HandyFlowException.class, () -> service.run(tenant, new BillingRunRequest("2026-09", false), actor));
        verify(hire, never()).raise(any(), any(), any(), any());
    }
    @Test void aRunRaisesTheChargesRecordsItselfAndIsAudited() {
        BillingRunResponse r = service.run(tenant, SEPT, false, actor, TODAY);
        assertTrue(r.applied());
        assertEquals(1, r.chargesCreated());
        var req = org.mockito.ArgumentCaptor.forClass(HireChargeRequest.class);
        verify(hire).raise(eq(tenant), req.capture(), eq(actor), eq(TODAY));
        assertEquals("DAILY", req.getValue().basis());
        assertEquals(LocalDate.of(2026, 9, 30), req.getValue().periodTo());
        var run = org.mockito.ArgumentCaptor.forClass(EarthBillingRun.class);
        verify(runs).save(run.capture());
        assertEquals(1, run.getValue().getChargesCreated());
        assertEquals("MANUAL", run.getValue().getTriggerSource());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_BILLING_RUN"), any(), any(), any(), any());
        verify(hire, never()).draftFrom(any(), any(), any());
    }
    @Test void runningTheMonthAgainRaisesNothingNew() {
        when(charges.findLiveOverlapping(any(), any(), any(), any())).thenReturn(List.of(pending(deployment, "2026-09-01", "2026-09-30")));
        BillingRunResponse r = service.run(tenant, SEPT, false, actor, TODAY);
        assertEquals(0, r.chargesCreated());
        assertEquals(1, r.alreadyBilled());
        verify(hire, never()).raise(any(), any(), any(), any());
    }
    @Test void oneChargeFailingNeverStopsTheOthers() {
        EarthAsset second = EarthAsset.create(tenant, "CAT 320", "E-02", "EXCAVATOR", null, null, null, null, null, "OWN", null, null, null, n("9000"), null, null);
        EarthDeployment d2 = onRun(second, "DAILY");
        when(assets.findActiveById(tenant, second.getId())).thenReturn(Optional.of(second));
        when(deployments.findAutoBilled(eq(tenant), any(), any())).thenReturn(List.of(deployment, d2));
        when(hire.raise(eq(tenant), argThat(r -> r != null && r.deploymentId().equals(deployment.getId())), any(), any()))
                .thenThrow(new IllegalStateException("Part of that period is already charged for this deployment."));
        BillingRunResponse r = service.run(tenant, SEPT, false, actor, TODAY);
        assertEquals(1, r.chargesCreated());
        assertEquals(1, r.exceptions());
        assertTrue(r.lines().stream().anyMatch(l -> "EXCEPTION".equals(l.state()) && l.message().contains("already charged")));
    }
    @Test void draftInvoicesAreMadeOnePerCustomerFromWaitingChargesOnly() {
        EarthHireCharge waiting = pending(deployment, "2026-09-01", "2026-09-30");
        EarthHireCharge held = pending(deployment, "2026-09-01", "2026-09-30");
        held.hold("An odometer reading is disputed");
        EarthHireCharge elsewhere = pending(onRun(asset, "DAILY"), "2026-09-01", "2026-09-30"); // not on this run
        when(charges.findPendingStartingBetween(eq(tenant), any(), any())).thenReturn(List.of(waiting, held, elsewhere));
        when(hire.draftFrom(eq(tenant), any(), any())).thenReturn(new DraftInvoiceResponse(UUID.randomUUID(), "INV-1", n("555000"), n("83250"), n("638250"), 1));
        BillingRunResponse r = service.run(tenant, SEPT, true, actor, TODAY);
        var req = org.mockito.ArgumentCaptor.forClass(DraftFromChargesRequest.class);
        verify(hire, times(1)).draftFrom(eq(tenant), req.capture(), eq(actor));
        assertEquals(List.of(waiting.getId()), req.getValue().chargeIds());
        assertEquals("Plant hire September 2026", req.getValue().title());
        assertEquals(1, r.invoices().size());
    }
    @Test void aRetryAfterAFailedInvoiceStepDraftsTheChargesStillWaiting() {
        EarthHireCharge waiting = pending(deployment, "2026-09-01", "2026-09-30");
        when(charges.findLiveOverlapping(any(), any(), any(), any())).thenReturn(List.of(waiting));
        when(charges.findPendingStartingBetween(eq(tenant), any(), any())).thenReturn(List.of(waiting));
        when(hire.draftFrom(eq(tenant), any(), any())).thenReturn(new DraftInvoiceResponse(UUID.randomUUID(), "INV-2", n("1"), n("0"), n("1"), 1));
        BillingRunResponse r = service.run(tenant, SEPT, true, actor, TODAY);
        assertEquals(0, r.chargesCreated());
        assertEquals(1, r.invoices().size());
        verify(hire, never()).raise(any(), any(), any(), any());
    }
    @Test void aFailedDraftInvoiceIsReportedNotHidden() {
        EarthHireCharge waiting = pending(deployment, "2026-09-01", "2026-09-30");
        when(charges.findPendingStartingBetween(eq(tenant), any(), any())).thenReturn(List.of(waiting));
        when(hire.draftFrom(eq(tenant), any(), any())).thenThrow(new IllegalArgumentException("That customer was not found."));
        BillingRunResponse r = service.run(tenant, SEPT, true, actor, TODAY);
        assertTrue(r.invoices().isEmpty());
        assertTrue(r.lines().stream().anyMatch(l -> l.message() != null && l.message().contains("draft invoice could not be made")));
    }

    // ── the schedule ─────────────────────────────────────────────────────────

    @Test void theScheduledRunNeedsNoUserAndMakesDraftInvoices() {
        granted.clear();
        EarthHireCharge waiting = pending(deployment, "2026-09-01", "2026-09-30");
        when(charges.findPendingStartingBetween(eq(tenant), any(), any())).thenReturn(List.of(waiting));
        when(hire.draftFrom(eq(tenant), any(), isNull())).thenReturn(new DraftInvoiceResponse(UUID.randomUUID(), "INV-3", n("1"), n("0"), n("1"), 1));
        BillingRunResponse r = service.runScheduled(tenant, SEPT, TODAY);
        assertNotNull(r);
        assertEquals(1, r.invoices().size());
        var marker = org.mockito.ArgumentCaptor.forClass(EarthBillingRun.class);
        verify(runs).saveAndFlush(marker.capture());
        assertEquals("SCHEDULED", marker.getValue().getTriggerSource());
        assertEquals(1, marker.getValue().getChargesCreated()); // filled in once it finished
    }
    @Test void aMonthThatAlreadyRanOnScheduleIsNotRunAgain() {
        when(runs.scheduledRunExists(tenant, LocalDate.of(2026, 9, 1))).thenReturn(true);
        assertNull(service.runScheduled(tenant, SEPT, TODAY));
        verify(hire, never()).raise(any(), any(), any(), any());
    }
    @Test void aSecondServerStartingTheSameMonthStopsAtTheDatabase() {
        when(runs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_em_billing_runs_scheduled"));
        assertNull(service.runScheduled(tenant, SEPT, TODAY));
        verify(hire, never()).raise(any(), any(), any(), any());
    }
    @Test void theSchedulerBillsLastMonthForEveryCompanyThatSwitchedItOn() {
        UUID other = UUID.randomUUID();
        when(settings.findTenantIdsWithAutoBilling()).thenReturn(List.of(tenant.getValue(), other));
        EarthMonthlyBillingService billing = mock(EarthMonthlyBillingService.class);
        when(billing.runScheduled(eq(TenantId.of(other)), any(), any())).thenThrow(new IllegalStateException("boom"));
        when(billing.runScheduled(eq(tenant), eq(SEPT), eq(TODAY))).thenReturn(new BillingRunResponse("2026-09", true, List.of(), 0, 0, 0, 0, List.of()));
        assertEquals(1, new EarthMonthlyBillingScheduler(settings, billing).sweep(TODAY));
    }

    // ── set-up ───────────────────────────────────────────────────────────────

    @Test void onlyAnAdministratorPutsADeploymentOnTheRunAndOnlyWithAValidBasis() {
        granted.clear();
        granted.add("EARTHMOVING_DEPLOY");
        assertThrows(HandyFlowException.class, () -> service.setDeploymentBasis(tenant, deployment.getId(), "MONTHLY", actor));
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.setDeploymentBasis(tenant, deployment.getId(), "FORTNIGHTLY", actor));
        assertEquals("MONTHLY", service.setDeploymentBasis(tenant, deployment.getId(), "monthly", actor).basis());
        assertNull(service.setDeploymentBasis(tenant, deployment.getId(), null, actor).basis());
        verify(trail, times(2)).record(eq(tenant), eq("EARTHMOVING_AUTO_BILLING_SET"), any(), eq(deployment.getId()), any(), any());
    }
    @Test void theScheduledRunIsSwitchedPerCompanyByAnAdministrator() {
        granted.clear();
        assertThrows(HandyFlowException.class, () -> service.switchScheduled(tenant, true, actor));
        granted.add("EARTHMOVING_ADMIN");
        granted.add("EARTHMOVING_READ");
        when(settings.findById(tenant.getValue())).thenReturn(Optional.empty());
        when(runs.findRecent(any(), any())).thenReturn(List.of());
        service.switchScheduled(tenant, true, actor);
        var saved = org.mockito.ArgumentCaptor.forClass(EarthSettings.class);
        verify(settings).save(saved.capture());
        assertTrue(saved.getValue().isAutoBillingEnabled());
    }
}
