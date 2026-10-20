package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.EarthTelematicsDevice;
import za.co.handyflow.platform.earthmoving.domain.model.EarthTelematicsReading;
import za.co.handyflow.platform.earthmoving.domain.model.TelematicsRules;
import za.co.handyflow.platform.earthmoving.domain.model.TelematicsRules.Freshness;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TelematicsRulesTest {
    Instant now = Instant.parse("2026-10-20T12:00:00Z");
    TenantId tenant = TenantId.generate();
    UUID asset = UUID.randomUUID(), actor = UUID.randomUUID();
    BigDecimal lat = new BigDecimal("-26.2041"), lon = new BigDecimal("28.0473");

    EarthTelematicsDevice device() { return EarthTelematicsDevice.map(tenant, asset, "acme_track", " DEV-1 ", " Dozer unit ", actor); }

    @Test void freshnessIsLastSeenNotLive() {
        assertEquals(Freshness.NEVER, TelematicsRules.freshness(null, now));
        assertEquals(Freshness.FRESH, TelematicsRules.freshness(now.minus(Duration.ofMinutes(30)), now));
        assertEquals(Freshness.STALE, TelematicsRules.freshness(now.minus(Duration.ofMinutes(31)), now));
        assertEquals(Freshness.STALE, TelematicsRules.freshness(now.minus(Duration.ofHours(24)), now));
        assertEquals(Freshness.OFFLINE, TelematicsRules.freshness(now.minus(Duration.ofHours(25)), now));
    }

    @Test void providerCodeAndDeviceIdAreChecked() {
        assertEquals("ACME_TRACK", TelematicsRules.providerCode(" acme_track "));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.providerCode("a"));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.providerCode("bad code!"));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.externalId(" "));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.externalId("x".repeat(121)));
    }

    @Test void aReadingNeedsAPositionOrAMeterAndRangesHold() {
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(lat, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(new BigDecimal("91"), lon, null, null));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(lat, new BigDecimal("-181"), null, null));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(null, null, new BigDecimal("-1"), "HOURS"));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(null, null, BigDecimal.TEN, null));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkReading(null, null, BigDecimal.TEN, "MILES"));
        assertDoesNotThrow(() -> TelematicsRules.checkReading(lat, lon, null, null));
        assertDoesNotThrow(() -> TelematicsRules.checkReading(null, null, BigDecimal.TEN, "hours"));
    }

    @Test void timeMustBeRecentAndNotInTheFuture() {
        assertDoesNotThrow(() -> TelematicsRules.checkTime(now.plus(Duration.ofMinutes(5)), now));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkTime(now.plus(Duration.ofMinutes(6)), now));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkTime(now.minus(Duration.ofDays(31)), now));
        assertThrows(IllegalArgumentException.class, () -> TelematicsRules.checkTime(null, now));
    }

    @Test void anOldReadingNeverReplacesANewerOne() {
        assertTrue(TelematicsRules.isNewer(now, null));
        assertTrue(TelematicsRules.isNewer(now, now.minusSeconds(1)));
        assertFalse(TelematicsRules.isNewer(now.minusSeconds(60), now));
        assertFalse(TelematicsRules.isNewer(now, now));
    }

    @Test void theMeterDifferenceIsReportedOnlyForTheSameUnit() {
        assertEquals(new BigDecimal("12.50"), TelematicsRules.meterDifference(new BigDecimal("1012.50"), "HOURS", new BigDecimal("1000.00"), "HOURS"));
        assertNull(TelematicsRules.meterDifference(new BigDecimal("1012.50"), "KILOMETRES", new BigDecimal("1000"), "HOURS"));
        assertNull(TelematicsRules.meterDifference(null, null, new BigDecimal("1000"), "HOURS"));
        assertEquals("HOURS", TelematicsRules.unitForMeter("HOURS"));
        assertEquals("KILOMETRES", TelematicsRules.unitForMeter("KILOMETRES"));
    }

    @Test void aDeviceIsMappedUnderANormalisedCodeAndUnmappedWithAReason() {
        EarthTelematicsDevice d = device();
        assertEquals("ACME_TRACK", d.getProviderCode());
        assertEquals("DEV-1", d.getExternalId());
        assertEquals("Dozer unit", d.getLabel());
        assertTrue(d.isActive());
        assertThrows(IllegalArgumentException.class, () -> d.unmap("no", actor));
        d.unmap("Unit removed from machine", actor);
        assertFalse(d.isActive());
        assertEquals(actor, d.getUnmappedBy());
        assertThrows(IllegalStateException.class, () -> d.unmap("Unit removed again", actor));
    }

    @Test void providerAndManualReadingsAreKeptApart() {
        EarthTelematicsReading p = EarthTelematicsReading.fromProvider(tenant, device(), now.minusSeconds(30), now, lat, lon, new BigDecimal("1200.5"), "hours");
        assertEquals("PROVIDER", p.getSource());
        assertEquals("ACME_TRACK", p.getProviderCode());
        assertEquals("HOURS", p.getMeterUnit());
        assertNull(p.getEnteredBy());
        EarthTelematicsReading m = EarthTelematicsReading.manual(tenant, asset, now, now, null, null, new BigDecimal("1199"), "HOURS", " read off dash ", actor);
        assertEquals("MANUAL", m.getSource());
        assertEquals(actor, m.getEnteredBy());
        assertEquals("read off dash", m.getNote());
        assertNull(m.getDeviceId());
    }

    @Test void aBadReadingIsRefusedWhenBuilt() {
        assertThrows(IllegalArgumentException.class, () -> EarthTelematicsReading.fromProvider(tenant, device(), now.plus(Duration.ofHours(1)), now, lat, lon, null, null));
        assertThrows(IllegalArgumentException.class, () -> EarthTelematicsReading.manual(tenant, asset, now, now, null, null, null, null, null, actor));
    }
}
