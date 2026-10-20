package za.co.handyflow.platform.fleet.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AssetRulesTest {
    TenantId tenant = TenantId.generate();
    UUID vehicle = UUID.randomUUID();
    UUID host = UUID.randomUUID();
    static BigDecimal d(String v) { return new BigDecimal(v); }

    AssetProfile profile(AssetClass c) {
        AssetProfile p = AssetProfile.create(tenant, vehicle);
        p.apply(c, null, d("8.0"), null, null, null, null, null, null);
        return p;
    }

    // ── Hour-based meters ──
    @Test void ptoAndHydraulicHoursOnlyCountUp() {
        AssetProfile p = profile(AssetClass.TRACTOR);
        p.updateMeters(d("100"), d("50"), d("500"));
        assertThrows(IllegalArgumentException.class, () -> p.updateMeters(d("99"), null, d("500")));
        assertThrows(IllegalArgumentException.class, () -> p.updateMeters(null, d("49"), d("500")));
        assertEquals(0, p.getPtoHours().compareTo(d("100")));
    }

    @Test void ptoAndHydraulicCannotExceedEngineHours() {
        AssetProfile p = profile(AssetClass.TRACTOR);
        assertThrows(IllegalArgumentException.class, () -> p.updateMeters(d("600"), null, d("500")));
        assertThrows(IllegalArgumentException.class, () -> p.updateMeters(null, d("501"), d("500")));
    }

    @Test void aNullReadingLeavesTheMeterAlone() {
        AssetProfile p = profile(AssetClass.PLANT);
        p.updateMeters(d("10"), d("5"), d("100"));
        p.updateMeters(null, null, d("100"));
        assertEquals(0, p.getPtoHours().compareTo(d("10")));
        assertEquals(0, p.getHydraulicHours().compareTo(d("5")));
    }

    // ── Hour-based servicing ──
    @Test void serviceIsDueOnceTheIntervalHasRun() {
        AssetProfile p = profile(AssetClass.PLANT);
        p.apply(AssetClass.PLANT, null, d("8"), 250, d("1000"), null, null, null, null);
        assertEquals(0, p.hoursSinceService(d("1200")).compareTo(d("200")));
        assertFalse(p.isServiceDueByHours(d("1249.9")));
        assertTrue(p.isServiceDueByHours(d("1250")));
        p.recordServiceAt(d("1260"));
        assertFalse(p.isServiceDueByHours(d("1300")));
    }

    @Test void withoutAnIntervalNothingIsEverDue() {
        AssetProfile p = profile(AssetClass.PLANT);
        assertNull(p.hoursSinceService(d("9999")));
        assertFalse(p.isServiceDueByHours(d("9999")));
    }

    @Test void theFirstConfiguredIntervalStartsFromTheCurrentHoursNotZero() {
        AssetProfile p = profile(AssetClass.PLANT);
        p.apply(AssetClass.PLANT, null, d("8"), 250, null, null, null, null, null);
        assertNull(p.getLastServiceHours());
        p.seedLastServiceHours(d("4000"));
        assertFalse(p.isServiceDueByHours(d("4100")));
        p.seedLastServiceHours(d("4200")); // already seeded: not moved
        assertEquals(0, p.getLastServiceHours().compareTo(d("4000")));
    }

    @Test void targetHoursAndIntervalAreRangeChecked() {
        AssetProfile p = profile(AssetClass.TRACTOR);
        assertThrows(IllegalArgumentException.class, () -> p.apply(AssetClass.TRACTOR, null, d("0"), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> p.apply(AssetClass.TRACTOR, null, d("24.1"), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> p.apply(AssetClass.TRACTOR, null, d("8"), 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> p.apply(AssetClass.TRACTOR, null, null, null, null, null, null, null, null));
    }

    // ── Trailers ──
    @Test void onlyATrailerCanBeCoupledAndOnlyOnce() {
        assertThrows(IllegalStateException.class, () -> profile(AssetClass.TRACTOR).couple(host));
        AssetProfile t = profile(AssetClass.TRAILER);
        t.couple(host);
        assertEquals(host, t.getCoupledToVehicleId());
        assertThrows(IllegalStateException.class, () -> t.couple(UUID.randomUUID()));
        t.uncouple();
        assertNull(t.getCoupledToVehicleId());
    }

    @Test void aTrailerCannotBeCoupledToItself() {
        assertThrows(IllegalArgumentException.class, () -> profile(AssetClass.TRAILER).couple(vehicle));
    }

    @Test void aCoupledTrailerMustBeUncoupledBeforeChangingClass() {
        AssetProfile t = profile(AssetClass.TRAILER);
        t.couple(host);
        assertThrows(IllegalStateException.class, () -> t.apply(AssetClass.PLANT, null, d("8"), null, null, null, null, null, null));
        t.uncouple();
        t.apply(AssetClass.PLANT, null, d("8"), null, null, null, null, null, null);
        assertEquals(AssetClass.PLANT, t.getAssetClass());
    }

    @Test void onlyHoursBasedClassesAreWorkedInHours() {
        assertTrue(AssetClass.TRACTOR.isHoursBased());
        assertTrue(AssetClass.FORKLIFT.isHoursBased());
        assertFalse(AssetClass.ROAD_VEHICLE.isHoursBased());
        assertFalse(AssetClass.HEAVY_VEHICLE.isHoursBased());
        assertFalse(AssetClass.TRAILER.isHoursBased());
    }

    // ── Implements ──
    AssetAttachment bucket() {
        return AssetAttachment.create(tenant, "600mm bucket", "BUCKET", "SN-1", null, null, null, null);
    }

    @Test void anImplementBanksTheHoursWorkedOnEachMachine() {
        AssetAttachment a = bucket();
        a.attach(vehicle, d("1000"), Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(AttachmentStatus.ATTACHED, a.getStatus());
        assertEquals(0, a.totalHoursInUse(d("1040")).compareTo(d("40")));
        a.detach(d("1050"));
        assertEquals(AttachmentStatus.AVAILABLE, a.getStatus());
        assertNull(a.getCurrentVehicleId());
        a.attach(host, d("200"), Instant.parse("2026-02-01T00:00:00Z"));
        a.detach(d("225"));
        assertEquals(0, a.getAccumulatedHours().compareTo(d("75")));
    }

    @Test void anImplementCannotBeOnTwoMachinesOrBeDetachedBelowItsStart() {
        AssetAttachment a = bucket();
        a.attach(vehicle, d("1000"), Instant.now());
        assertThrows(IllegalStateException.class, () -> a.attach(host, d("5"), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> a.detach(d("999")));
        assertThrows(IllegalStateException.class, a::retire);
    }

    @Test void anImplementThatIsNotOnAMachineCannotBeDetachedAndARetiredOneCannotBeUsed() {
        AssetAttachment a = bucket();
        assertThrows(IllegalStateException.class, () -> a.detach(d("1")));
        a.retire();
        assertEquals(AttachmentStatus.RETIRED, a.getStatus());
        assertThrows(IllegalStateException.class, () -> a.attach(vehicle, d("1"), Instant.now()));
    }
}
