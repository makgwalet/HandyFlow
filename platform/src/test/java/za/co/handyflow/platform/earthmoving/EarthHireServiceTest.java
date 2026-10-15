package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthHireServiceTest {
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    static final LocalDate STARTED = LocalDate.of(2026, 10, 1);

    EarthDeploymentRepository deployments = mock(EarthDeploymentRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthHireChargeRepository charges = mock(EarthHireChargeRepository.class);
    OperatorLogRepository logs = mock(OperatorLogRepository.class);
    EarthMeterReadingRepository meters = mock(EarthMeterReadingRepository.class);
    EarthContractRateRepository contractRates = mock(EarthContractRateRepository.class);
    EarthMeterDisputeRepository disputes = mock(EarthMeterDisputeRepository.class);
    CrmFacade crm = mock(CrmFacade.class);
    InvoicingFacade invoicing = mock(InvoicingFacade.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthHireService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    UUID customer = UUID.randomUUID();
    EarthAsset asset;
    EarthDeployment deployment;

    static BigDecimal n(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        service = new EarthHireService(deployments, assets, charges, logs, meters, contractRates, disputes, crm, invoicing, access, trail);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", "Caterpillar", "D9T", 2020, "S1", "GP1",
                "OWN", null, null, null, n("18500"), n("2200"), null);
        deployment = EarthDeployment.create(tenant, asset.getId(), "Mine A", "Anglo", null, null, STARTED, null, null).withLinks(null, customer, null);
        ReflectionTestUtils.setField(deployment, "deployedAt", STARTED.atStartOfDay(ZONE).toInstant());
        when(deployments.findByTenantAndId(tenant, deployment.getId())).thenReturn(Optional.of(deployment));
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
        when(crm.customerExists(tenant, customer)).thenReturn(true);
        when(charges.findLiveOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        when(charges.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(charges.save(any())).thenAnswer(i -> i.getArgument(0));
        when(logs.sumHoursBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(meters.findByAsset(any(TenantId.class), any(), any())).thenReturn(List.of());
        when(disputes.findOpenForReadings(any(), any())).thenReturn(List.of());
    }

    HireChargeRequest daily(String from, String to) {
        return new HireChargeRequest(deployment.getId(), "DAILY", LocalDate.parse(from), LocalDate.parse(to), null, null, null);
    }
    EarthHireCharge pending(UUID forCustomer, String from, String to) {
        return EarthHireCharge.create(tenant, deployment.getId(), asset.getId(), forCustomer, LocalDate.parse(from), LocalDate.parse(to),
                "DAILY", n("3.00"), n("1000"), null, "Hire: D9", actor);
    }

    // ── permissions ──────────────────────────────────────────────────────────

    HireChargeRequest atRate(String rate, String reason) {
        return new HireChargeRequest(deployment.getId(), "DAILY", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), null,
                rate == null ? null : n(rate), reason);
    }

    @Test void anAgreedRateReplacesTheMachinesRateForThatChargeOnly() {
        granted.add("EARTHMOVING_ADMIN");
        HireChargeResponse r = service.create(tenant, atRate("15000", "Long-term hire discount agreed with the client"), actor, TODAY);
        assertEquals(n("15000.00"), r.unitRate());
        assertEquals(n("45000.00"), r.amount());
        assertTrue(r.note().contains("Long-term hire discount"));
        assertEquals(n("18500"), asset.getDailyRate());
    }

    @Test void aDifferentRateNeedsAReasonAndMustBePositive() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, atRate("15000", null), actor, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, atRate("15000", "disc"), actor, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, atRate("0", "Agreed free of charge"), actor, TODAY));
        verify(charges, never()).saveAndFlush(any());
    }

    @Test void raisingAChargeNeedsAdmin() {
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(HandyFlowException.class, () -> service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY));
        assertThrows(HandyFlowException.class, () -> service.preview(tenant, daily("2026-10-01", "2026-10-03"), TODAY));
        assertThrows(HandyFlowException.class, () -> service.voidCharge(tenant, UUID.randomUUID(), "billed in error", actor));
        assertThrows(HandyFlowException.class, () -> service.reopen(tenant, UUID.randomUUID(), "draft was deleted", actor));
        assertThrows(HandyFlowException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(UUID.randomUUID()), null, null), actor));
        verify(charges, never()).saveAndFlush(any());
    }

    @Test void readingNeedsRead() {
        assertThrows(HandyFlowException.class, () -> service.list(tenant, null));
        granted.add("EARTHMOVING_READ");
        when(charges.findForTenant(tenant, null)).thenReturn(List.of());
        assertTrue(service.list(tenant, null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.list(tenant, "NOPE"));
    }

    // ── raising ──────────────────────────────────────────────────────────────

    @Test void aDailyChargeCountsDaysAtTheDailyRate() {
        granted.add("EARTHMOVING_ADMIN");
        HireChargeResponse r = service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY);
        assertEquals(n("3.00"), r.quantity());
        assertEquals(n("18500.00"), r.unitRate());
        assertEquals(n("55500.00"), r.amount());
        assertEquals(n("8325.00"), r.vatAmount());
        assertEquals(n("63825.00"), r.total());
        assertEquals("PENDING", r.status());
        verify(charges).saveAndFlush(any(EarthHireCharge.class));
        verify(trail).record(eq(tenant), eq("EARTHMOVING_HIRE_CHARGE_CREATED"), any(), any(), any(), any());
    }

    @Test void previewSavesNothing() {
        granted.add("EARTHMOVING_ADMIN");
        HireChargeResponse r = service.preview(tenant, daily("2026-10-01", "2026-10-03"), TODAY);
        assertEquals("PREVIEW", r.status());
        assertNull(r.id());
        verify(charges, never()).saveAndFlush(any());
        verify(charges, never()).save(any());
    }

    @Test void anHourlyChargeUsesTheHoursOnCompletedShifts() {
        granted.add("EARTHMOVING_ADMIN");
        when(logs.sumHoursBetween(eq(asset.getId()), any(), any())).thenReturn(n("12.5"));
        HireChargeResponse r = service.create(tenant, new HireChargeRequest(deployment.getId(), "HOURLY", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), null, null, null), actor, TODAY);
        assertEquals(n("12.50"), r.quantity());
        assertEquals(n("27500.00"), r.amount());
    }

    @Test void anHourlyChargeWithNoShiftsIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant,
                new HireChargeRequest(deployment.getId(), "HOURLY", STARTED, STARTED.plusDays(2), null, null, null), actor, TODAY));
        verify(charges, never()).saveAndFlush(any());
    }

    @Test void aDeploymentWithoutACustomerCannotBeCharged() {
        granted.add("EARTHMOVING_ADMIN");
        deployment.withLinks(null, null, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY));
        assertTrue(e.getMessage().contains("not linked to a customer"));
    }

    @Test void aCustomerThatNoLongerExistsIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        when(crm.customerExists(tenant, customer)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY));
    }

    @Test void aMachineWithoutTheRateCannotBeCharged() {
        granted.add("EARTHMOVING_ADMIN");
        EarthAsset noRate = EarthAsset.create(tenant, "Bakkie", "B-01", "LDV", null, null, null, null, null, "OWN", null, null, null, null, null, null);
        when(assets.findActiveById(tenant, deployment.getAssetId())).thenReturn(Optional.of(noRate));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY));
    }

    @Test void aPeriodBeforeTheDeploymentOrInTheFutureIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, daily("2026-09-28", "2026-10-03"), actor, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, daily("2026-10-10", "2026-10-20"), actor, TODAY));
    }

    @Test void aPeriodAlreadyChargedIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        when(charges.findLiveOverlapping(eq(tenant), eq(deployment.getId()), any(), any())).thenReturn(List.of(pending(customer, "2026-10-01", "2026-10-05")));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.create(tenant, daily("2026-10-04", "2026-10-08"), actor, TODAY));
        assertTrue(e.getMessage().contains("already charged"));
        verify(charges, never()).saveAndFlush(any());
    }

    @Test void twoPeopleRaisingTheSamePeriodAtOnceAreCaughtByTheDatabase() {
        granted.add("EARTHMOVING_ADMIN");
        when(charges.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ex_em_hire_no_overlap"));
        assertThrows(IllegalStateException.class, () -> service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY));
        verify(trail, never()).record(any(), eq("EARTHMOVING_HIRE_CHARGE_CREATED"), any(), any(), any(), any());
    }

    @Test void anotherCompanysDeploymentIsNotFound() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(ResourceNotFoundException.class, () -> service.create(TenantId.generate(), daily("2026-10-01", "2026-10-03"), actor, TODAY));
    }

    // ── voiding and reopening ────────────────────────────────────────────────

    @Test void aPendingChargeCanBeVoidedWithAReason() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge c = pending(customer, "2026-10-01", "2026-10-03");
        when(charges.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
        assertThrows(IllegalArgumentException.class, () -> service.voidCharge(tenant, c.getId(), " ", actor));
        service.voidCharge(tenant, c.getId(), "billed in error", actor);
        assertEquals("VOIDED", c.getStatus());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_HIRE_CHARGE_VOIDED"), any(), eq(c.getId()), any(), any());
    }

    @Test void aChargeOnAnExistingDraftCannotBeReopened() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge c = pending(customer, "2026-10-01", "2026-10-03");
        c.markInvoiced(UUID.randomUUID(), "INV-9");
        when(charges.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
        when(invoicing.findInvoiceStatus(tenant, c.getInvoiceId())).thenReturn(Optional.of("DRAFT"));
        assertThrows(IllegalStateException.class, () -> service.reopen(tenant, c.getId(), "draft was deleted", actor));
        assertEquals("INVOICED", c.getStatus());
    }

    @Test void aChargeWhoseInvoiceIsGoneOrCancelledCanBeReopened() {
        granted.add("EARTHMOVING_ADMIN");
        for (Optional<String> gone : List.of(Optional.<String>empty(), Optional.of("CANCELLED"))) {
            EarthHireCharge c = pending(customer, "2026-10-01", "2026-10-03");
            c.markInvoiced(UUID.randomUUID(), "INV-9");
            when(charges.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
            when(invoicing.findInvoiceStatus(tenant, c.getInvoiceId())).thenReturn(gone);
            service.reopen(tenant, c.getId(), "draft was deleted", actor);
            assertEquals("PENDING", c.getStatus());
        }
    }

    @Test void aChargeThatIsPaidOrIssuedCannotBeReopened() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge c = pending(customer, "2026-10-01", "2026-10-03");
        c.markInvoiced(UUID.randomUUID(), "INV-9");
        when(charges.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
        for (String status : List.of("ISSUED", "PARTIALLY_PAID", "PAID", "OVERDUE")) {
            when(invoicing.findInvoiceStatus(tenant, c.getInvoiceId())).thenReturn(Optional.of(status));
            assertThrows(IllegalStateException.class, () -> service.reopen(tenant, c.getId(), "draft was deleted", actor));
        }
    }

    // ── the draft invoice ────────────────────────────────────────────────────

    private void found(EarthHireCharge... cs) {
        List<UUID> ids = Arrays.stream(cs).map(EarthHireCharge::getId).toList();
        when(charges.findByTenantAndIds(eq(tenant), any())).thenReturn(List.of(cs));
    }

    @Test void pendingChargesOfOneCustomerGoOnOneDraftInvoice() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge a = pending(customer, "2026-10-06", "2026-10-08");
        EarthHireCharge b = pending(customer, "2026-10-01", "2026-10-03");
        found(a, b);
        UUID invoiceId = UUID.randomUUID();
        when(invoicing.createDraftInvoice(eq(tenant), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(invoiceId, "FPS-INV-00012", n("6000.00"), n("900.00"), n("6900.00")));

        DraftInvoiceResponse r = service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(a.getId(), b.getId()), null, null), actor);

        var request = org.mockito.ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(eq(tenant), request.capture());
        assertEquals(customer, request.getValue().customerId());
        assertEquals("Plant hire", request.getValue().title());
        assertEquals(2, request.getValue().lines().size());
        assertEquals("day", request.getValue().lines().get(0).unit());
        assertEquals(n("1000"), request.getValue().lines().get(0).unitPrice());
        assertEquals(n("15.00"), request.getValue().lines().get(0).vatRate());
        assertEquals("FPS-INV-00012", r.invoiceNumber());
        assertEquals(2, r.charges());
        assertEquals("INVOICED", a.getStatus());
        assertEquals("INVOICED", b.getStatus());
        assertEquals(invoiceId, a.getInvoiceId());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_HIRE_INVOICED"), any(), any(), any(), any());
    }

    @Test void chargesForDifferentCustomersCannotShareAnInvoice() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge a = pending(customer, "2026-10-01", "2026-10-03");
        EarthHireCharge b = pending(UUID.randomUUID(), "2026-10-04", "2026-10-05");
        found(a, b);
        assertThrows(IllegalArgumentException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(a.getId(), b.getId()), null, null), actor));
        verify(invoicing, never()).createDraftInvoice(any(), any());
        assertEquals("PENDING", a.getStatus());
    }

    @Test void aChargeAlreadyOnAnInvoiceOrVoidedCannotGoOnAnother() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge done = pending(customer, "2026-10-01", "2026-10-03");
        done.markInvoiced(UUID.randomUUID(), "INV-1");
        found(done);
        assertThrows(IllegalStateException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(done.getId()), null, null), actor));
        EarthHireCharge voided = pending(customer, "2026-10-04", "2026-10-05");
        voided.voidCharge("billed in error");
        found(voided);
        assertThrows(IllegalStateException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(voided.getId()), null, null), actor));
        verify(invoicing, never()).createDraftInvoice(any(), any());
    }

    @Test void aMissingOrForeignChargeStopsTheInvoice() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge a = pending(customer, "2026-10-01", "2026-10-03");
        when(charges.findByTenantAndIds(eq(tenant), any())).thenReturn(List.of(a));
        assertThrows(IllegalArgumentException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(a.getId(), UUID.randomUUID()), null, null), actor));
        assertThrows(IllegalArgumentException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(), null, null), actor));
        verify(invoicing, never()).createDraftInvoice(any(), any());
    }

    @Test void ifInvoicingRefusesNothingIsMarkedInvoiced() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge a = pending(customer, "2026-10-01", "2026-10-03");
        found(a);
        when(invoicing.createDraftInvoice(eq(tenant), any())).thenThrow(new IllegalArgumentException("That customer was not found."));
        assertThrows(IllegalArgumentException.class, () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(a.getId()), "Plant hire", null), actor));
        assertEquals("PENDING", a.getStatus());
        verify(charges, never()).saveAll(any());
    }

    // ── kilometre billing ────────────────────────────────────────────────────

    static final java.time.Instant BEFORE_PERIOD = java.time.Instant.parse("2026-09-30T10:00:00Z");
    static final java.time.Instant IN_PERIOD = java.time.Instant.parse("2026-10-10T10:00:00Z");

    EarthMeterReading odo(String km, java.time.Instant at) {
        EarthMeterReading r = EarthMeterReading.of(tenant, asset.getId(), "KILOMETRES", n(km), null, "MANUAL", null, actor);
        ReflectionTestUtils.setField(r, "recordedAt", at);
        return r;
    }
    HireChargeRequest km(String rate, String reason, String included) {
        return new HireChargeRequest(deployment.getId(), "KILOMETRE", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"), null,
                rate == null ? null : n(rate), reason, included == null ? null : n(included));
    }
    /** The odometer at the start of the period (recorded the day before) and at its end; null leaves that reading out. */
    List<EarthMeterReading> odometer(String start, String end) {
        asset.changeMeter("KILOMETRES", n("0"), null, null);
        List<EarthMeterReading> rows = new ArrayList<>();
        if (end != null) rows.add(odo(end, IN_PERIOD));
        if (start != null) rows.add(odo(start, BEFORE_PERIOD));
        when(meters.findByAsset(any(TenantId.class), any(), any())).thenReturn(rows);
        return rows;
    }

    @Test void kilometresAreChargedFromTheOdometerAtTheAgreedRate() {
        granted.add("EARTHMOVING_ADMIN");
        odometer("10000", "10850");
        HireChargeResponse r = service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY);
        assertEquals("KILOMETRE", r.basis());
        assertEquals(n("850.00"), r.quantity());
        assertEquals(n("10625.00"), r.amount());
        assertTrue(r.description().contains("odometer 10000 to 10850"));
        assertEquals("AGREED", r.rateSource());
    }
    @Test void anIncludedAllowanceIsNotCharged() {
        granted.add("EARTHMOVING_ADMIN");
        odometer("10000", "10850");
        HireChargeResponse r = service.create(tenant, km("12.50", "Per-km rate in the signed contract", "500"), actor, TODAY);
        assertEquals(n("350.00"), r.quantity());
        assertTrue(r.description().contains("500 km included"));
        assertEquals(n("500.00"), r.includedKm());
    }
    @Test void kilometresNeedARateAndReadingsAtBothEnds() {
        granted.add("EARTHMOVING_ADMIN");
        odometer("10000", "10850");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, km(null, null, null), actor, TODAY));
        odometer(null, "10850");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY));
        odometer("10000", null);
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY));
        verify(charges, never()).saveAndFlush(any());
    }
    @Test void anHourMeterMachineCannotBeBilledByKilometre() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY));
    }
    @Test void aDisputedReadingStopsAKilometreChargeBeingRaised() {
        granted.add("EARTHMOVING_ADMIN");
        List<EarthMeterReading> rows = odometer("10000", "10850");
        var open = EarthMeterDispute.raise(tenant, asset.getId(), rows.get(0).getId(), "Driver says 10800", actor);
        when(disputes.findOpenForReadings(eq(tenant), any())).thenReturn(List.of(open));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY));
        assertTrue(e.getMessage().contains("disputed"));
        verify(charges, never()).saveAndFlush(any());
    }
    @Test void aCorrectedReadingIsUsedEvenForAPeriodThatEndedBeforeTheCorrection() {
        granted.add("EARTHMOVING_ADMIN");
        List<EarthMeterReading> rows = odometer("10000", "10900");
        EarthMeterReading fix = EarthMeterReading.correctionOf(tenant, asset.getId(), "KILOMETRES", n("10850"), n("10900"), "Typed twice", actor, rows.get(0).getId());
        ReflectionTestUtils.setField(fix, "recordedAt", java.time.Instant.parse("2026-10-12T08:00:00Z"));
        List<EarthMeterReading> all = new ArrayList<>(rows);
        all.add(0, fix);
        when(meters.findByAsset(any(TenantId.class), any(), any())).thenReturn(all);
        assertEquals(n("850.00"), service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY).quantity());
    }

    // ── weeks, months and the rate on the contract ───────────────────────────

    UUID contractId = UUID.randomUUID();
    void contractRate(String basis, String rate, String included) {
        deployment.linkContract(contractId);
        when(contractRates.findByContractAndBasis(tenant, contractId, basis))
                .thenReturn(Optional.of(EarthContractRate.of(tenant, contractId, basis, n(rate), included == null ? null : n(included), actor)));
    }
    HireChargeRequest on(String basis, String from, String to) {
        return new HireChargeRequest(deployment.getId(), basis, LocalDate.parse(from), LocalDate.parse(to), null, null, null);
    }

    @Test void weeksAreChargedAtTheWeeklyRateOnTheContract() {
        granted.add("EARTHMOVING_ADMIN");
        contractRate("WEEKLY", "9000", null);
        HireChargeResponse r = service.create(tenant, on("WEEKLY", "2026-10-01", "2026-10-08"), actor, TODAY);
        assertEquals(n("2.00"), r.quantity());
        assertEquals(n("18000.00"), r.amount());
        assertEquals("CONTRACT", r.rateSource());
        assertTrue(r.description().contains("2 weeks @ R9000.00"));
    }
    @Test void monthsAreChargedAtTheMonthlyRateOnTheContract() {
        granted.add("EARTHMOVING_ADMIN");
        contractRate("MONTHLY", "30000", null);
        HireChargeResponse r = service.create(tenant, on("MONTHLY", "2026-10-01", "2026-10-15"), actor, TODAY);
        assertEquals(n("1.00"), r.quantity());
        assertEquals(n("30000.00"), r.amount());
    }
    @Test void aWeeklyOrMonthlyChargeNeedsARateFromTheContractOrAgreedForIt() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, on("WEEKLY", "2026-10-01", "2026-10-08"), actor, TODAY));
        HireChargeResponse r = service.create(tenant, new HireChargeRequest(deployment.getId(), "WEEKLY", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-07"), null, n("8500"), "Agreed by phone with the client"), actor, TODAY);
        assertEquals("AGREED", r.rateSource());
        assertEquals(n("8500.00"), r.amount());
    }
    @Test void theContractRateBeatsTheMachinesAndAnAgreedRateBeatsTheContracts() {
        granted.add("EARTHMOVING_ADMIN");
        contractRate("DAILY", "16000", null);
        HireChargeResponse fromContract = service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY);
        assertEquals(n("16000.00"), fromContract.unitRate());
        assertEquals("CONTRACT", fromContract.rateSource());
        HireChargeResponse agreed = service.create(tenant, atRate("15000", "Discount agreed for this month only"), actor, TODAY);
        assertEquals(n("15000.00"), agreed.unitRate());
        assertEquals("AGREED", agreed.rateSource());
    }
    @Test void aMachineRateIsUsedWhenTheContractHasNoRateForThatBasis() {
        granted.add("EARTHMOVING_ADMIN");
        contractRate("WEEKLY", "9000", null);
        HireChargeResponse r = service.create(tenant, daily("2026-10-01", "2026-10-03"), actor, TODAY);
        assertEquals(n("18500.00"), r.unitRate());
        assertEquals("ASSET", r.rateSource());
    }
    @Test void aKilometreRateAndAllowanceCanBothComeFromTheContract() {
        granted.add("EARTHMOVING_ADMIN");
        odometer("10000", "10850");
        contractRate("KILOMETRE", "12.50", "500");
        HireChargeResponse r = service.create(tenant, km(null, null, null), actor, TODAY);
        assertEquals(n("350.00"), r.quantity());
        assertEquals(n("4375.00"), r.amount());
        assertEquals("CONTRACT", r.rateSource());
        assertEquals(n("500.00"), r.includedKm());
        HireChargeResponse own = service.create(tenant, km(null, null, "0"), actor, TODAY);
        assertEquals(n("850.00"), own.quantity());
    }

    // ── a reading changed: recalculate, hold, release ────────────────────────

    /** A pending kilometre charge as it was raised: 10000 to 10900, 900 km at R12.50. */
    EarthHireCharge kmCharge(EarthMeterReading start, EarthMeterReading end) {
        EarthHireCharge c = EarthHireCharge.create(tenant, deployment.getId(), asset.getId(), customer, LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-10"), "KILOMETRE", n("900"), n("12.50"), null, "Hire: D9 (odometer 10000 to 10900; 900 km @ R12.50)", actor);
        c.recordRate("AGREED");
        c.recordKilometres(null, start.getId(), start.getReading(), end.getId(), end.getReading());
        return c;
    }
    List<EarthMeterReading> afterCorrection(EarthMeterReading start, EarthMeterReading wrongEnd) {
        EarthMeterReading fix = EarthMeterReading.correctionOf(tenant, asset.getId(), "KILOMETRES", n("10850"), n("10900"), "Typed twice", actor, wrongEnd.getId());
        ReflectionTestUtils.setField(fix, "recordedAt", java.time.Instant.parse("2026-10-12T08:00:00Z"));
        return new ArrayList<>(List.of(fix, wrongEnd, start));
    }

    @Test void aCorrectedReadingRecalculatesThePendingChargeAtTheSameRate() {
        List<EarthMeterReading> rows = odometer("10000", "10900");
        EarthHireCharge c = kmCharge(rows.get(1), rows.get(0));
        when(charges.findLiveKilometreByAsset(tenant, asset.getId())).thenReturn(List.of(c));
        when(meters.findByAsset(any(TenantId.class), any(), any())).thenReturn(afterCorrection(rows.get(1), rows.get(0)));

        EarthHireService.Refresh r = service.refreshKilometreCharges(tenant, asset.getId(), actor, "reading corrected");

        assertEquals(1, r.recalculated());
        assertEquals(n("850.00"), c.getQuantity());
        assertEquals(n("10625.00"), c.getAmount());
        assertEquals(n("12.50"), c.getUnitRate());
        assertTrue(c.getDescription().contains("10000 to 10850"));
        assertNotNull(c.getRecalculatedAt());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_HIRE_CHARGE_RECALCULATED"), any(), eq(c.getId()), any(), any());
    }
    @Test void aChargeWhoseReadingsHaveNotChangedIsLeftAlone() {
        List<EarthMeterReading> rows = odometer("10000", "10900");
        EarthHireCharge c = kmCharge(rows.get(1), rows.get(0));
        when(charges.findLiveKilometreByAsset(tenant, asset.getId())).thenReturn(List.of(c));
        EarthHireService.Refresh r = service.refreshKilometreCharges(tenant, asset.getId(), actor, "reading corrected");
        assertEquals(new EarthHireService.Refresh(0, 0, 0), r);
        assertNull(c.getRecalculatedAt());
        verify(charges, never()).save(any());
    }
    @Test void aDisputedReadingHoldsTheChargeAndConfirmingItReleasesIt() {
        List<EarthMeterReading> rows = odometer("10000", "10900");
        EarthHireCharge c = kmCharge(rows.get(1), rows.get(0));
        when(charges.findLiveKilometreByAsset(tenant, asset.getId())).thenReturn(List.of(c));
        var open = EarthMeterDispute.raise(tenant, asset.getId(), rows.get(0).getId(), "Driver says 10850", actor);
        when(disputes.findOpenForReadings(eq(tenant), any())).thenReturn(List.of(open));

        assertEquals(1, service.refreshKilometreCharges(tenant, asset.getId(), actor, "reading disputed").held());
        assertTrue(c.getAttention().contains("Driver says 10850"));
        assertEquals(n("900.00"), c.getQuantity());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_HIRE_CHARGE_HELD"), any(), eq(c.getId()), any(), any());
        // Asking again changes nothing.
        clearInvocations(trail);
        assertEquals(0, service.refreshKilometreCharges(tenant, asset.getId(), actor, "again").held());
        verify(trail, never()).record(any(), any(), any(), any(), any(), any());

        when(disputes.findOpenForReadings(eq(tenant), any())).thenReturn(List.of());
        service.refreshKilometreCharges(tenant, asset.getId(), actor, "dispute confirmed");
        assertNull(c.getAttention());
        assertEquals(n("900.00"), c.getQuantity());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_HIRE_CHARGE_RELEASED"), any(), eq(c.getId()), any(), any());
    }
    @Test void aCorrectionThatLeavesNothingToChargeHoldsTheChargeWithTheReason() {
        List<EarthMeterReading> rows = odometer("10000", "10900");
        EarthHireCharge c = kmCharge(rows.get(1), rows.get(0));
        when(charges.findLiveKilometreByAsset(tenant, asset.getId())).thenReturn(List.of(c));
        EarthMeterReading fix = EarthMeterReading.correctionOf(tenant, asset.getId(), "KILOMETRES", n("9000"), n("10900"), "Typed wrongly", actor, rows.get(0).getId());
        ReflectionTestUtils.setField(fix, "recordedAt", java.time.Instant.parse("2026-10-12T08:00:00Z"));
        when(meters.findByAsset(any(TenantId.class), any(), any())).thenReturn(List.of(fix, rows.get(0), rows.get(1)));
        assertEquals(1, service.refreshKilometreCharges(tenant, asset.getId(), actor, "reading corrected").held());
        assertTrue(c.getAttention().contains("lower than"));
        assertEquals(n("900.00"), c.getQuantity());
    }
    @Test void anInvoicedChargeIsNeverChangedOnlyCounted() {
        List<EarthMeterReading> rows = odometer("10000", "10900");
        EarthHireCharge c = kmCharge(rows.get(1), rows.get(0));
        c.markInvoiced(UUID.randomUUID(), "INV-7");
        when(charges.findLiveKilometreByAsset(tenant, asset.getId())).thenReturn(List.of(c));
        when(meters.findByAsset(any(TenantId.class), any(), any())).thenReturn(afterCorrection(rows.get(1), rows.get(0)));
        EarthHireService.Refresh r = service.refreshKilometreCharges(tenant, asset.getId(), actor, "reading corrected");
        assertEquals(new EarthHireService.Refresh(0, 0, 1), r);
        assertEquals(n("900.00"), c.getQuantity());
        assertEquals("INVOICED", c.getStatus());
    }
    @Test void aChargeOnHoldCannotGoOnAnInvoice() {
        granted.add("EARTHMOVING_ADMIN");
        EarthHireCharge c = pending(customer, "2026-10-01", "2026-10-03");
        c.hold("An odometer reading is disputed: Driver says 10850");
        found(c);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.draftInvoice(tenant, new DraftFromChargesRequest(List.of(c.getId()), null, null), actor));
        assertTrue(e.getMessage().contains("on hold"));
        verify(invoicing, never()).createDraftInvoice(any(), any());
        c.release();
        assertNull(c.getAttention());
    }
}
