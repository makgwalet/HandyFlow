package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthMeterServiceTest {
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthMeterReadingRepository readings = mock(EarthMeterReadingRepository.class);
    EarthMaintenancePlanRepository plans = mock(EarthMaintenancePlanRepository.class);
    EarthAlertService alerts = mock(EarthAlertService.class);
    EarthMeterDisputeRepository disputes = mock(EarthMeterDisputeRepository.class);
    EarthHireService hire = mock(EarthHireService.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthMeterService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    EarthAsset asset;

    static BigDecimal n(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        service = new EarthMeterService(assets, readings, plans, alerts, disputes, hire, access, trail);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null,
                "OWN", null, null, null, null, null, null);
        asset.updateHours(n("5000"));
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
        when(readings.save(any())).thenAnswer(i -> i.getArgument(0));
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of());
        when(plans.findForAsset(any(), any())).thenReturn(List.of());
        when(disputes.findOpenForReadings(any(), any())).thenReturn(List.of());
        when(disputes.findByAsset(any(), any())).thenReturn(List.of());
        when(disputes.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(disputes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(hire.refreshKilometreCharges(any(), any(), any(), any())).thenReturn(new EarthHireService.Refresh(0, 0, 0));
    }

    EarthMeterReading last(String source, String prev) {
        return EarthMeterReading.of(tenant, asset.getId(), "HOURS", n("5000"), prev == null ? null : n(prev), source, null, null);
    }

    @Test void recordWritesARowOnlyWhenTheNumberChanged() {
        service.record(tenant, asset, n("4900"), "MANUAL", actor);
        verify(readings).save(any(EarthMeterReading.class));
        clearInvocations(readings);
        service.record(tenant, asset, n("5000"), "MANUAL", actor);
        verify(readings, never()).save(any());
    }

    @Test void correctingNeedsMaintain() {
        granted.add("EARTHMOVING_WRITE");
        assertThrows(HandyFlowException.class, () -> service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("500"), "typed an extra zero"), actor));
        assertEquals(n("5000"), asset.getCurrentHours());
    }

    @Test void aCorrectionLowersTheReadingKeepsAReasonAndIsAudited() {
        granted.add("EARTHMOVING_MAINTAIN");
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(last("MANUAL", "480")));
        var r = service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("500"), "typed an extra zero"), actor);
        assertEquals(n("500"), asset.getCurrentHours());
        assertEquals("CORRECTION", r.source());
        assertEquals(n("5000"), r.previousReading());
        assertEquals("typed an extra zero", r.reason());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_METER_CORRECTED"), any(), eq(asset.getId()), any(), any());
    }

    @Test void aCorrectionWithoutAReasonChangesNothing() {
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(IllegalArgumentException.class, () -> service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("500"), ""), actor));
        assertEquals(n("5000"), asset.getCurrentHours());
        verify(readings, never()).save(any());
    }

    @Test void aCorrectionCannotGoBelowTheReadingBeforeIt() {
        granted.add("EARTHMOVING_MAINTAIN");
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(last("MANUAL", "4800")));
        assertThrows(IllegalArgumentException.class, () -> service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("4000"), "typed wrongly"), actor));
        assertEquals(n("5000"), asset.getCurrentHours());
    }

    @Test void theSameReadingCannotBeCorrectedTwice() {
        granted.add("EARTHMOVING_MAINTAIN");
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(last("CORRECTION", "9000")));
        assertThrows(IllegalStateException.class, () -> service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("4900"), "typed wrongly"), actor));
    }

    @Test void aCorrectionThatClearsTheServiceDueAlertResolvesIt() {
        granted.add("EARTHMOVING_MAINTAIN");
        asset.recordService(n("4000"));   // 5000 - 4000 = 1000 >= 250: due
        assertTrue(asset.isDueForService());
        service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("4100"), "typed an extra digit"), actor);
        assertFalse(asset.isDueForService());
        verify(alerts).resolveByKey(eq(tenant), eq("SERVICE_DUE:" + asset.getId()), eq(actor), any());
    }

    @Test void aCorrectionBelowTheLastServiceIsRefused() {
        granted.add("EARTHMOVING_MAINTAIN");
        asset.recordService(n("4500"));
        assertThrows(IllegalStateException.class, () -> service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("4000"), "typed wrongly"), actor));
    }

    @Test void anotherCompanysMachineIsNotFound() {
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(ResourceNotFoundException.class, () -> service.correct(TenantId.generate(), asset.getId(), new CorrectReadingRequest(n("1"), "typed wrongly"), actor));
    }

    @Test void changingTheMeterIsAdminOnly() {
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(HandyFlowException.class, () -> service.change(tenant, asset.getId(),
                new ChangeMeterRequest("KILOMETRES", n("48000"), n("45000"), n("5000"), "truck odometer"), actor));
        assertEquals("HOURS", asset.getMeterType());
    }

    @Test void changingTheMeterRestartsTheNumbersAndKeepsTheHistory() {
        granted.add("EARTHMOVING_ADMIN");
        var r = service.change(tenant, asset.getId(), new ChangeMeterRequest("KILOMETRES", n("48000"), n("45000"), n("5000"), "truck odometer"), actor);
        assertEquals("KILOMETRES", asset.getMeterType());
        assertEquals(n("48000"), asset.getCurrentHours());
        assertEquals(n("45000"), asset.getLastServiceHours());
        assertEquals(n("5000"), asset.getServiceIntervalHours());
        assertEquals("TYPE_CHANGE", r.source());
        assertEquals(n("5000"), r.previousReading());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_METER_CHANGED"), any(), eq(asset.getId()), any(), any());
    }

    @Test void changingTheMeterIsRefusedWhileMeterBasedPlansAreActive() {
        granted.add("EARTHMOVING_ADMIN");
        EarthMaintenancePlan p = EarthMaintenancePlan.create(tenant, asset.getId(), "Service", "SERVICE", "HOURS", 250, 25, n("5000"),
                null, "NORMAL", true);
        when(plans.findForAsset(tenant, asset.getId())).thenReturn(List.of(p));
        assertThrows(IllegalStateException.class, () -> service.change(tenant, asset.getId(),
                new ChangeMeterRequest("KILOMETRES", n("48000"), n("45000"), n("5000"), "truck odometer"), actor));
        assertEquals("HOURS", asset.getMeterType());
    }

    @Test void historyNeedsReadAndIsTenantScoped() {
        assertThrows(HandyFlowException.class, () -> service.history(tenant, asset.getId()));
        granted.add("EARTHMOVING_READ");
        assertTrue(service.history(tenant, asset.getId()).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.history(TenantId.generate(), asset.getId()));
    }

    // ── disputed readings ────────────────────────────────────────────────────

    EarthMeterReading odometer(String km, String source, String prev) {
        EarthMeterReading r = EarthMeterReading.of(tenant, asset.getId(), "KILOMETRES", n(km), prev == null ? null : n(prev), source, null, null);
        when(readings.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }
    DisputeReadingRequest because(String why) { return new DisputeReadingRequest(why); }

    @Test void disputingAReadingNeedsMaintainOrAdmin() {
        granted.add("EARTHMOVING_READ");
        EarthMeterReading r = odometer("10850", "MANUAL", "10000");
        assertThrows(HandyFlowException.class, () -> service.dispute(tenant, asset.getId(), r.getId(), because("Driver says 10800"), actor));
        verify(disputes, never()).saveAndFlush(any());
    }
    @Test void aKilometreReadingCanBeDisputedAndTheHeldChargesAreReported() {
        granted.add("EARTHMOVING_ADMIN");
        EarthMeterReading r = odometer("10850", "MANUAL", "10000");
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(r));
        when(hire.refreshKilometreCharges(tenant, asset.getId(), actor, "reading disputed")).thenReturn(new EarthHireService.Refresh(0, 2, 0));
        var resp = service.dispute(tenant, asset.getId(), r.getId(), because("Driver says 10800"), actor);
        assertEquals("OPEN", resp.disputeStatus());
        assertEquals("Driver says 10800", resp.disputeReason());
        assertEquals(2, resp.chargesHeld());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_METER_DISPUTED"), any(), eq(asset.getId()), any(), any());
    }
    @Test void aDisputeNeedsAReasonAndCannotBeRaisedTwice() {
        granted.add("EARTHMOVING_MAINTAIN");
        EarthMeterReading r = odometer("10850", "MANUAL", "10000");
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(r));
        assertThrows(IllegalArgumentException.class, () -> service.dispute(tenant, asset.getId(), r.getId(), because(" "), actor));
        when(disputes.findOpenForReadings(any(), any())).thenReturn(List.of(EarthMeterDispute.raise(tenant, asset.getId(), r.getId(), "Already queried", actor)));
        assertThrows(IllegalStateException.class, () -> service.dispute(tenant, asset.getId(), r.getId(), because("Driver says 10800"), actor));
    }
    @Test void anHourReadingOrAReplacedReadingCannotBeDisputed() {
        granted.add("EARTHMOVING_MAINTAIN");
        EarthMeterReading hours = EarthMeterReading.of(tenant, asset.getId(), "HOURS", n("5000"), null, "MANUAL", null, null);
        when(readings.findById(hours.getId())).thenReturn(Optional.of(hours));
        assertThrows(IllegalArgumentException.class, () -> service.dispute(tenant, asset.getId(), hours.getId(), because("Looks too high"), actor));
        EarthMeterReading old = odometer("10900", "MANUAL", "10000");
        EarthMeterReading fix = EarthMeterReading.correctionOf(tenant, asset.getId(), "KILOMETRES", n("10850"), n("10900"), "Typed twice", actor, old.getId());
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(fix, old));
        assertThrows(IllegalStateException.class, () -> service.dispute(tenant, asset.getId(), old.getId(), because("Looks too high"), actor));
    }
    @Test void aReadingOfAnotherMachineIsNotFound() {
        granted.add("EARTHMOVING_MAINTAIN");
        EarthMeterReading other = EarthMeterReading.of(tenant, UUID.randomUUID(), "KILOMETRES", n("10850"), null, "MANUAL", null, null);
        when(readings.findById(other.getId())).thenReturn(Optional.of(other));
        assertThrows(ResourceNotFoundException.class, () -> service.dispute(tenant, asset.getId(), other.getId(), because("Driver says 10800"), actor));
    }
    @Test void confirmingNeedsMaintainAndANoteAndReleasesHeldCharges() {
        EarthMeterReading r = odometer("10850", "MANUAL", "10000");
        var d = EarthMeterDispute.raise(tenant, asset.getId(), r.getId(), "Driver says 10800", actor);
        when(disputes.findByTenantAndId(tenant, d.getId())).thenReturn(Optional.of(d));
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(HandyFlowException.class, () -> service.confirm(tenant, asset.getId(), d.getId(), new ResolveDisputeRequest("Logbook agrees"), actor));
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(tenant, asset.getId(), d.getId(), new ResolveDisputeRequest(" "), actor));
        var resp = service.confirm(tenant, asset.getId(), d.getId(), new ResolveDisputeRequest("Logbook agrees"), actor);
        assertEquals("CONFIRMED", resp.disputeStatus());
        verify(hire).refreshKilometreCharges(tenant, asset.getId(), actor, "dispute confirmed");
        verify(trail).record(eq(tenant), eq("EARTHMOVING_METER_DISPUTE_CONFIRMED"), any(), eq(asset.getId()), any(), any());
    }
    @Test void correctingADisputedReadingClosesTheDisputeAndRecalculatesCharges() {
        granted.add("EARTHMOVING_MAINTAIN");
        asset.changeMeter("KILOMETRES", n("10900"), n("9000"), n("1000"));
        EarthMeterReading wrong = odometer("10900", "MANUAL", "10000");
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(wrong));
        var d = EarthMeterDispute.raise(tenant, asset.getId(), wrong.getId(), "Driver says 10850", actor);
        when(disputes.findOpenForReadings(any(), any())).thenReturn(List.of(d));
        when(hire.refreshKilometreCharges(tenant, asset.getId(), actor, "reading corrected")).thenReturn(new EarthHireService.Refresh(1, 0, 0));

        var resp = service.correct(tenant, asset.getId(), new CorrectReadingRequest(n("10850"), "Typed it twice"), actor);

        assertEquals(wrong.getId(), resp.correctsId());
        assertEquals(1, resp.chargesRecalculated());
        assertEquals("CORRECTED", d.getStatus());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_METER_DISPUTE_CORRECTED"), any(), eq(asset.getId()), any(), any());
    }
    @Test void historyShowsTheLatestDisputeOnEachReading() {
        granted.add("EARTHMOVING_READ");
        EarthMeterReading r = odometer("10850", "MANUAL", "10000");
        var d = EarthMeterDispute.raise(tenant, asset.getId(), r.getId(), "Driver says 10800", actor);
        when(readings.findByAsset(any(), any(), any())).thenReturn(List.of(r));
        when(disputes.findByAsset(tenant, asset.getId())).thenReturn(List.of(d));
        var rows = service.history(tenant, asset.getId());
        assertEquals("OPEN", rows.get(0).disputeStatus());
        assertEquals(d.getId(), rows.get(0).disputeId());
    }
}
