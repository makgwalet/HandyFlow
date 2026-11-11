package za.co.handyflow.platform.fuel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 4 (D02): cash tolerance, fleet-card tender, adjustments, supervisor review. */
class FuelRetailControlsTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID OPENER = UUID.randomUUID(), OTHER = UUID.randomUUID(), SUPERVISOR = UUID.randomUUID();

    static BigDecimal d(String s) { return new BigDecimal(s); }
    static FuelShift open() { return FuelShift.open(T, "SH-1", "Thabo", OPENER, null); }
    static final BigDecimal Z = BigDecimal.ZERO;

    @Test @DisplayName("the original close() keeps the R5.00 tolerance and queues nothing for review inside it")
    void legacyCloseUnchanged() {
        FuelShift s = open();
        s.close(d("4000"), d("1998"), Z, Z, d("6000"), d("300"), null, OPENER);
        assertEquals("CLOSED", s.getStatus());
        assertEquals("NONE", s.getReviewStatus());
        assertEquals(0, d("-2").compareTo(s.getVarianceAmount()));
    }

    @Test @DisplayName("fleet-card takings count towards what was taken")
    void fleetCardCounts() {
        FuelShift s = open();
        s.close(d("3000"), d("2000"), d("1000"), Z, Z, d("6000"), Z, d("300"), null, OPENER, d("5.00"), false);
        assertEquals(0, Z.compareTo(s.getVarianceAmount()));
        assertEquals(0, d("1000").compareTo(s.getFleetCardAmount()));
    }

    @Test @DisplayName("approved adjustments lower what the shift is expected to hold")
    void adjustmentsLowerExpected() {
        FuelShift s = open();
        s.close(d("5900"), Z, Z, Z, Z, d("6000"), d("100"), d("300"), null, OPENER, d("5.00"), false);
        assertEquals(0, Z.compareTo(s.getVarianceAmount()));
        assertEquals(0, d("6000").compareTo(s.getExpectedAmount()));
        assertEquals(0, d("100").compareTo(s.getAdjustmentsAmount()));
    }

    @Test @DisplayName("a company-set tolerance replaces the constant")
    void configurableTolerance() {
        FuelShift tight = open();
        assertThrows(IllegalArgumentException.class, () -> tight.close(d("5998"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OPENER, d("1.00"), false));
        FuelShift loose = open();
        loose.close(d("5980"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OPENER, d("50.00"), false);
        assertEquals("NONE", loose.getReviewStatus());
        assertEquals(0, d("50.00").compareTo(loose.getToleranceApplied()));
    }

    @Test @DisplayName("outside the tolerance needs a reason and is queued for review")
    void outsideToleranceQueuesReview() {
        FuelShift s = open();
        s.close(d("5900"), Z, Z, Z, Z, d("6000"), Z, d("300"), "Drive-off reported to manager", OPENER, d("5.00"), false);
        assertEquals("PENDING_REVIEW", s.getReviewStatus());
        assertTrue(s.isOutsideTolerance());
    }

    @Test @DisplayName("exactly on the tolerance is inside it")
    void boundaryInside() {
        FuelShift s = open();
        s.close(d("5995"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OPENER, d("5.00"), false);
        assertEquals("NONE", s.getReviewStatus());
        assertFalse(s.isOutsideTolerance());
    }

    @Test @DisplayName("separate-closer rule: the opener cannot close; someone else can; off by default")
    void separateCloser() {
        FuelShift a = open();
        assertThrows(IllegalStateException.class, () -> a.close(d("6000"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OPENER, d("5.00"), true));
        assertTrue(a.isOpen());
        a.close(d("6000"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OTHER, d("5.00"), true);
        assertEquals("CLOSED", a.getStatus());
        FuelShift b = open();
        b.close(d("6000"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OPENER, d("5.00"), false);
        assertEquals("CLOSED", b.getStatus());
    }

    @Test @DisplayName("review: a supervisor other than the closer, with a note, once")
    void reviewRules() {
        FuelShift s = open();
        s.close(d("5900"), Z, Z, Z, Z, d("6000"), Z, d("300"), "Drive-off reported to manager", OTHER, d("5.00"), false);
        assertThrows(IllegalStateException.class, () -> s.review(OTHER, "I closed it myself"));
        assertThrows(IllegalArgumentException.class, () -> s.review(SUPERVISOR, "ok"));
        s.review(SUPERVISOR, "Checked CCTV, drive-off confirmed");
        assertEquals("REVIEWED", s.getReviewStatus());
        assertEquals(SUPERVISOR, s.getReviewedBy());
        assertThrows(IllegalStateException.class, () -> s.review(SUPERVISOR, "Checked again"));
    }

    @Test @DisplayName("review: a shift inside tolerance has nothing to review")
    void nothingToReview() {
        FuelShift s = open();
        s.close(d("6000"), Z, Z, Z, Z, d("6000"), Z, d("300"), null, OTHER, d("5.00"), false);
        assertThrows(IllegalStateException.class, () -> s.review(SUPERVISOR, "Not needed really"));
    }

    @Test @DisplayName("negative fleet-card amount is refused")
    void negativeFleetRefused() {
        assertThrows(IllegalArgumentException.class, () -> open().close(d("6000"), Z, d("-1"), Z, Z, d("6000"), Z, d("300"), null, OTHER, d("5.00"), false));
    }

    // ── adjustments ──

    @Test @DisplayName("adjustment: validates kind, amount and reason")
    void adjustmentValidation() {
        UUID shift = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> FuelShiftAdjustment.record(T, shift, "GIFT", d("10"), "Because reasons", OPENER));
        assertThrows(IllegalArgumentException.class, () -> FuelShiftAdjustment.record(T, shift, "REFUND", Z, "Wrong pump used", OPENER));
        assertThrows(IllegalArgumentException.class, () -> FuelShiftAdjustment.record(T, shift, "REFUND", d("10"), "no", OPENER));
        assertTrue(FuelShiftAdjustment.record(T, shift, "VOID", d("10"), "Wrong pump used", OPENER).isPending());
    }

    @Test @DisplayName("adjustment: the person who recorded it cannot approve or reject it")
    void adjustmentFourEyes() {
        FuelShiftAdjustment a = FuelShiftAdjustment.record(T, UUID.randomUUID(), "REFUND", d("50"), "Customer overfilled", OPENER);
        assertThrows(IllegalStateException.class, () -> a.approve(OPENER, null));
        assertThrows(IllegalStateException.class, () -> a.reject(OPENER, "Not valid at all"));
        a.approve(SUPERVISOR, null);
        assertTrue(a.isApproved());
        assertThrows(IllegalStateException.class, () -> a.approve(SUPERVISOR, null));
    }

    @Test @DisplayName("adjustment: rejection needs a reason; only the recorder can withdraw, only while pending")
    void adjustmentRejectAndWithdraw() {
        FuelShiftAdjustment a = FuelShiftAdjustment.record(T, UUID.randomUUID(), "DISCOUNT", d("20"), "Loyalty promotion", OPENER);
        assertThrows(IllegalArgumentException.class, () -> a.reject(SUPERVISOR, " "));
        assertThrows(IllegalStateException.class, () -> a.withdraw(SUPERVISOR));
        a.withdraw(OPENER);
        assertEquals("WITHDRAWN", a.getStatus());
        assertThrows(IllegalStateException.class, () -> a.withdraw(OPENER));
    }

    @Test @DisplayName("settings: cash controls default to the old R5.00 and off; null leaves them alone; negative refused")
    void settingsDefaults() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertEquals(0, d("5.00").compareTo(s.getCashVarianceTolerance()));
        assertFalse(s.isRequireSeparateCloser());
        s.updateCashControls(d("10"), true);
        s.updateCashControls(null, null);
        assertEquals(0, d("10").compareTo(s.getCashVarianceTolerance()));
        assertTrue(s.isRequireSeparateCloser());
        assertThrows(IllegalArgumentException.class, () -> s.updateCashControls(d("-1"), null));
    }
}
