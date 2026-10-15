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
        service = new EarthHireService(deployments, assets, charges, logs, meters, crm, invoicing, access, trail);
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
        when(meters.findBefore(any(TenantId.class), any(), any(), any())).thenReturn(List.of());
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

    EarthMeterReading odo(String km) {
        return EarthMeterReading.of(tenant, asset.getId(), "KILOMETRES", n(km), null, "MANUAL", null, actor);
    }
    HireChargeRequest km(String rate, String reason, String included) {
        return new HireChargeRequest(deployment.getId(), "KILOMETRE", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"), null,
                rate == null ? null : n(rate), reason, included == null ? null : n(included));
    }
    /** Readings answer by moment: the start-of-period query gets the first value, the end-of-period query the second. */
    void odometer(String start, String end) {
        asset.changeMeter("KILOMETRES", n("0"), null, null);
        var startAt = LocalDate.parse("2026-10-01").atStartOfDay(ZONE).toInstant();
        when(meters.findBefore(any(TenantId.class), any(), eq(startAt), any())).thenReturn(start == null ? List.of() : List.of(odo(start)));
        when(meters.findBefore(any(TenantId.class), any(), eq(LocalDate.parse("2026-10-11").atStartOfDay(ZONE).toInstant()), any()))
                .thenReturn(end == null ? List.of() : List.of(odo(end)));
    }

    @Test void kilometresAreChargedFromTheOdometerAtTheAgreedRate() {
        granted.add("EARTHMOVING_ADMIN");
        odometer("10000", "10850");
        HireChargeResponse r = service.create(tenant, km("12.50", "Per-km rate in the signed contract", null), actor, TODAY);
        assertEquals("KILOMETRE", r.basis());
        assertEquals(n("850.00"), r.quantity());
        assertEquals(n("10625.00"), r.amount());
        assertTrue(r.description().contains("odometer 10000 to 10850"));
    }
    @Test void anIncludedAllowanceIsNotCharged() {
        granted.add("EARTHMOVING_ADMIN");
        odometer("10000", "10850");
        HireChargeResponse r = service.create(tenant, km("12.50", "Per-km rate in the signed contract", "500"), actor, TODAY);
        assertEquals(n("350.00"), r.quantity());
        assertTrue(r.description().contains("500 km included"));
    }
    @Test void kilometresNeedAnAgreedRateAndReadingsAtBothEnds() {
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
}
