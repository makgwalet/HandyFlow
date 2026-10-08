package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.GrowthRules.Point;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** All numbers below are SYNTHETIC and exist only to exercise the arithmetic. They are not growth reference data. */
class GrowthRulesTest {
    private static final List<Point> SYN = List.of(new Point(0, 1, 10, 0.1), new Point(12, 1, 20, 0.1));

    @Test void ageIsWholeMonthsPlusFraction() {
        assertEquals(12.0, GrowthRules.ageMonths(LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15)), 1e-9);
        assertEquals(0.5, GrowthRules.ageMonths(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 16)), 0.02);
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.ageMonths(null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.ageMonths(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1)));
    }

    @Test void interpolatesButNeverExtrapolates() {
        assertEquals(15.0, GrowthRules.at(SYN, 6).orElseThrow().m(), 1e-9);
        assertEquals(10.0, GrowthRules.at(SYN, 0).orElseThrow().m(), 1e-9);
        assertTrue(GrowthRules.at(SYN, 12.5).isEmpty());
        assertTrue(GrowthRules.at(List.of(), 1).isEmpty());
    }

    @Test void zScoreIsZeroAtTheMedianAndSignedAround() {
        Point p = new Point(6, 1, 15, 0.1);
        assertEquals(0.0, GrowthRules.zScore(15, p), 1e-9);
        assertEquals(1.0, GrowthRules.zScore(16.5, p), 1e-9);   // (16.5/15 - 1) / 0.1
        assertEquals(-1.0, GrowthRules.zScore(13.5, p), 1e-9);
        assertEquals(1.0, GrowthRules.zScore(15 * Math.exp(0.1), new Point(6, 0, 15, 0.1)), 1e-9);   // L = 0 uses the log form
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.zScore(0, p));
    }

    @Test void curveValueIsTheInverseOfZScore() {
        Point p = new Point(6, 1, 15, 0.1);
        for (double z : new double[]{-3, -1, 0, 2}) assertEquals(z, GrowthRules.zScore(GrowthRules.valueAtZ(z, p), p), 1e-9);
        Point q = new Point(6, 0, 15, 0.1);
        assertEquals(1.5, GrowthRules.zScore(GrowthRules.valueAtZ(1.5, q), q), 1e-9);
    }

    @Test void percentileOfStandardNormal() {
        assertEquals(50.0, GrowthRules.percentile(0), 1e-4);
        assertEquals(84.134, GrowthRules.percentile(1), 1e-2);
        assertEquals(2.275, GrowthRules.percentile(-2), 1e-2);
        assertEquals(99.865, GrowthRules.percentile(3), 1e-2);
    }

    @Test void importedPointsAreValidated() {
        GrowthRules.requirePoints(SYN, 0, 12);
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.requirePoints(List.of(new Point(0, 1, 10, 0.1)), 0, 12));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.requirePoints(List.of(new Point(0, 1, 10, 0.1), new Point(0, 1, 11, 0.1), new Point(12, 1, 12, 0.1)), 0, 12));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.requirePoints(List.of(new Point(0, 1, 0, 0.1), new Point(12, 1, 12, 0.1)), 0, 12));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.requirePoints(List.of(new Point(0, 1, 10, 0.1), new Point(11, 1, 12, 0.1)), 0, 12));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.requirePoints(List.of(new Point(0, 1, 10, 0.1), new Point(13, 1, 12, 0.1)), 0, 12));
    }

    @Test void lifecycleAndSeparationOfDuties() {
        GrowthRules.requireMove("DRAFT", "CLINICAL_REVIEW");
        GrowthRules.requireMove("CLINICAL_REVIEW", "APPROVED");
        GrowthRules.requireMove("APPROVED", "ACTIVE");
        GrowthRules.requireMove("ACTIVE", "RETIRED");
        assertThrows(IllegalStateException.class, () -> GrowthRules.requireMove("DRAFT", "ACTIVE"));
        assertThrows(IllegalStateException.class, () -> GrowthRules.requireMove("RETIRED", "ACTIVE"));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        GrowthRules.requireDifferentActivator(a, b);
        assertThrows(IllegalStateException.class, () -> GrowthRules.requireDifferentActivator(a, a));
        assertThrows(IllegalStateException.class, () -> GrowthRules.requireDifferentActivator(null, b));
    }

    @Test void noActiveSetMeansTheBannerShows() {
        assertEquals("DATA NOT CLINICALLY APPROVED", GrowthRules.banner(false));
        assertNull(GrowthRules.banner(true));
        assertEquals("WEIGHT", GrowthRules.measure(" weight "));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.measure("BP_SYSTOLIC"));
        assertThrows(IllegalArgumentException.class, () -> GrowthRules.sex("UNKNOWN"));
    }
}
