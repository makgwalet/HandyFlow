package za.co.handyflow.platform.fuel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelDistributionControlsTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID D = UUID.randomUUID(), ALICE = UUID.randomUUID(), BOB = UUID.randomUUID();
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    // ── variance ─────────────────────────────────────────────────────────────
    @Test @DisplayName("no tolerance set means nothing is flagged, however large the difference")
    void noToleranceNoVariance() { assertNull(FuelDeliveryVariance.ifOutside(T, D, bd("10000"), bd("5000"), null)); }

    @Test @DisplayName("a difference exactly at the tolerance is accepted; above it is flagged, with the sign kept")
    void toleranceBoundary() {
        assertNull(FuelDeliveryVariance.ifOutside(T, D, bd("10000"), bd("9950"), bd("0.5")));
        FuelDeliveryVariance v = FuelDeliveryVariance.ifOutside(T, D, bd("10000"), bd("9949"), bd("0.5"));
        assertNotNull(v);
        assertEquals(0, bd("-51").compareTo(v.getVarianceLitres()));
        assertEquals(0, bd("0.510").compareTo(v.getVariancePct()));
    }

    @Test @DisplayName("over-delivery is flagged as well as short delivery")
    void overDelivery() { assertNotNull(FuelDeliveryVariance.ifOutside(T, D, bd("1000"), bd("1100"), bd("1"))); }

    @Test @DisplayName("nothing is flagged when nothing was loaded")
    void zeroLoaded() { assertNull(FuelDeliveryVariance.ifOutside(T, D, BigDecimal.ZERO, bd("100"), bd("1"))); }

    @Test @DisplayName("closing needs a decision, a note and a reviewer; a closed variance is final")
    void closeRules() {
        FuelDeliveryVariance v = FuelDeliveryVariance.ifOutside(T, D, bd("1000"), bd("900"), bd("1"));
        assertThrows(IllegalArgumentException.class, () -> v.close(ALICE, "MAYBE", "Looked into it"));
        assertThrows(IllegalArgumentException.class, () -> v.close(ALICE, "EXPLAINED", "no"));
        assertThrows(IllegalStateException.class, () -> v.close(null, "EXPLAINED", "Meter drift confirmed"));
        v.investigate(ALICE, "Checking the meter");
        assertEquals("INVESTIGATING", v.getStatus());
        v.close(BOB, "WRITTEN_OFF", "Spillage at the depot");
        assertEquals("CLOSED", v.getStatus());
        assertEquals(BOB, v.getReviewedBy());
        assertThrows(IllegalStateException.class, () -> v.close(BOB, "EXPLAINED", "Changed my mind"));
        assertThrows(IllegalStateException.class, () -> v.investigate(BOB, "Again please"));
    }

    // ── evidence ─────────────────────────────────────────────────────────────
    private static FuelDeliveryEvidence photo() { return FuelDeliveryEvidence.create(T, D, "PHOTO", "files/pod-1.jpg", null, null, null, "Receiver at the tank", ALICE); }

    @Test @DisplayName("a GPS record needs valid coordinates; a file record needs a file")
    void creationRules() {
        assertThrows(IllegalArgumentException.class, () -> FuelDeliveryEvidence.create(T, D, "GPS", null, null, bd("28.0"), null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelDeliveryEvidence.create(T, D, "GPS", null, bd("91"), bd("28.0"), null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelDeliveryEvidence.create(T, D, "GPS", null, bd("-26.2"), bd("181"), null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelDeliveryEvidence.create(T, D, "PHOTO", " ", null, null, null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelDeliveryEvidence.create(T, D, "VIDEO", "x", null, null, null, null, ALICE));
        assertNotNull(FuelDeliveryEvidence.create(T, D, "GPS", null, bd("-26.2041"), bd("28.0473"), bd("12.5"), null, ALICE));
    }

    @Test @DisplayName("the capturer cannot verify; someone else can, and verifying twice is harmless")
    void verifyRules() {
        FuelDeliveryEvidence e = photo();
        assertThrows(IllegalStateException.class, () -> e.verify(ALICE));
        e.verify(BOB);
        assertTrue(e.isVerified());
        e.verify(BOB);
        assertEquals(BOB, e.getVerifiedBy());
    }

    @Test @DisplayName("verified evidence is locked and cannot be replaced")
    void lockedAfterVerify() {
        FuelDeliveryEvidence e = photo(); e.verify(BOB);
        assertThrows(IllegalStateException.class, () -> e.supersede(ALICE, "Wrong photo"));
        assertEquals("ACTIVE", e.getStatus());
    }

    @Test @DisplayName("unverified evidence can be replaced once, with a reason, and a replaced record cannot be verified")
    void supersedeRules() {
        FuelDeliveryEvidence e = photo();
        assertThrows(IllegalArgumentException.class, () -> e.supersede(ALICE, "no"));
        e.supersede(ALICE, "Photo was of the wrong tank");
        assertEquals("SUPERSEDED", e.getStatus());
        assertThrows(IllegalStateException.class, () -> e.supersede(ALICE, "Again for good measure"));
        assertThrows(IllegalStateException.class, () -> e.verify(BOB));
    }

    @Test @DisplayName("the tolerance setting is a percentage from 0 to 100, or empty")
    void toleranceSetting() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertNull(s.getVarianceTolerancePct());
        s.updateVarianceTolerance(bd("0.5"));
        assertEquals(0, bd("0.5").compareTo(s.getVarianceTolerancePct()));
        assertThrows(IllegalArgumentException.class, () -> s.updateVarianceTolerance(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> s.updateVarianceTolerance(bd("101")));
        s.updateVarianceTolerance(null);
        assertNull(s.getVarianceTolerancePct());
    }
}
