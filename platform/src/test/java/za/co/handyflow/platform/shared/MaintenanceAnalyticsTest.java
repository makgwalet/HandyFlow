package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.MaintenanceAnalytics.AssetStats;
import za.co.handyflow.platform.shared.MaintenanceAnalytics.Job;
import za.co.handyflow.platform.shared.MaintenanceAnalytics.VendorStats;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceAnalyticsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID vendorA = UUID.randomUUID();
    private final UUID vendorB = UUID.randomUUID();
    private final UUID pump = UUID.randomUUID();
    private final UUID lift = UUID.randomUUID();

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private static Instant day(int d) { return T0.plus(d, ChronoUnit.DAYS); }

    /** A completed job for a vendor that took {@code hours}. */
    private Job done(UUID vendor, String name, int hours, String cost, int rework, Boolean met) {
        return new Job(null, vendor, name, true, false, true, T0, T0.plus(hours, ChronoUnit.HOURS), null, null, rework, bd(cost), met);
    }

    private Job open(UUID vendor) {
        return new Job(null, vendor, "V", true, false, false, T0, null, null, null, 0, null, null);
    }

    private Job cancelled(UUID vendor) {
        return new Job(null, vendor, "V", true, true, false, T0, null, null, null, 0, null, null);
    }

    private Job failure(UUID asset, int createdDay, Integer downtimeMin, String cause, boolean completed) {
        return new Job(asset, null, null, true, false, completed, day(createdDay), completed ? day(createdDay).plus(4, ChronoUnit.HOURS) : null,
                downtimeMin, cause, 0, bd("100.00"), null);
    }

    // ── vendors ──────────────────────────────────────────────────────────────

    @Test
    void jobsWithoutAVendorAreIgnored() {
        assertTrue(MaintenanceAnalytics.vendorPerformance(List.of(done(null, null, 2, "10", 0, null))).isEmpty());
    }

    @Test
    void vendorTotalsSeparateCompletedCancelledAndOpen() {
        VendorStats s = MaintenanceAnalytics.vendorPerformance(List.of(
                done(vendorA, "ACME", 2, "100.00", 0, null), done(vendorA, "ACME", 4, "200.00", 0, null),
                open(vendorA), cancelled(vendorA))).get(0);
        assertEquals(4, s.jobs());
        assertEquals(2, s.completed());
        assertEquals(1, s.cancelled());
        assertEquals(1, s.open());
        assertEquals(0, bd("3.00").compareTo(s.avgResolutionHours()));
        assertEquals(0, bd("300.00").compareTo(s.totalCost()));
        assertEquals(0, bd("150.00").compareTo(s.avgCost()));
    }

    @Test
    void reworkRateIsOfCompletedJobsOnly() {
        VendorStats s = MaintenanceAnalytics.vendorPerformance(List.of(
                done(vendorA, "ACME", 1, "10", 1, null), done(vendorA, "ACME", 1, "10", 0, null),
                done(vendorA, "ACME", 1, "10", 2, null), done(vendorA, "ACME", 1, "10", 0, null), open(vendorA))).get(0);
        assertEquals(2, s.reworkJobs());
        assertEquals(0, bd("50.00").compareTo(s.reworkRatePercent()));
    }

    @Test
    void slaComplianceCountsOnlyConcludedJobs() {
        VendorStats s = MaintenanceAnalytics.vendorPerformance(List.of(
                done(vendorA, "ACME", 1, "1", 0, true), done(vendorA, "ACME", 1, "1", 0, true),
                done(vendorA, "ACME", 1, "1", 0, true), done(vendorA, "ACME", 1, "1", 0, false),
                done(vendorA, "ACME", 1, "1", 0, null), open(vendorA))).get(0);
        assertEquals(3, s.slaMet());
        assertEquals(1, s.slaBreached());
        assertEquals(0, bd("75.00").compareTo(s.slaCompliancePercent()));
    }

    @Test
    void noSlaMeansNoCompliancePercent() {
        VendorStats s = MaintenanceAnalytics.vendorPerformance(List.of(done(vendorA, "ACME", 1, "1", 0, null))).get(0);
        assertNull(s.slaCompliancePercent());
    }

    @Test
    void aVendorWithNothingCompletedHasNoAveragesAndNoRework() {
        VendorStats s = MaintenanceAnalytics.vendorPerformance(List.of(open(vendorA))).get(0);
        assertNull(s.avgResolutionHours());
        assertNull(s.avgCost());
        assertNull(s.reworkRatePercent());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.totalCost()));
    }

    @Test
    void busiestVendorComesFirst() {
        List<VendorStats> out = MaintenanceAnalytics.vendorPerformance(List.of(
                open(vendorB), open(vendorA), open(vendorA), open(vendorA)));
        assertEquals(vendorA, out.get(0).vendorId());
        assertEquals(vendorB, out.get(1).vendorId());
    }

    // ── assets ───────────────────────────────────────────────────────────────

    @Test
    void plannedWorkInspectionsAndCancelledJobsAreNotFailures() {
        Job ppm = new Job(pump, null, null, false, false, true, day(1), day(2), null, null, 0, null, null);
        Job cancelledFault = new Job(pump, null, null, true, true, false, day(3), null, null, null, 0, null, null);
        assertTrue(MaintenanceAnalytics.assetReliability(List.of(ppm, cancelledFault), T0, day(100), 10).isEmpty());
    }

    @Test
    void jobsWithoutAnAssetAreIgnored() {
        Job j = new Job(null, null, null, true, false, true, day(1), day(2), null, null, 0, null, null);
        assertTrue(MaintenanceAnalytics.assetReliability(List.of(j), T0, day(100), 10).isEmpty());
    }

    @Test
    void mtbfIsOperatingTimeOverFailures() {
        // 100-day period = 2400h; 2 failures with 10h total downtime => (2400-10)/2 = 1195h
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(
                failure(pump, 10, 300, "Seal", true), failure(pump, 60, 300, "Seal", true)), T0, day(100), 10).get(0);
        assertEquals(2, s.failures());
        assertEquals(600, s.downtimeMinutes());
        assertEquals(0, bd("1195.00").compareTo(s.mtbfHours()));
    }

    @Test
    void mttrIsAverageRepairTimeOfCompletedFailuresOnly() {
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(
                failure(pump, 10, null, null, true), failure(pump, 20, null, null, false)), T0, day(100), 10).get(0);
        assertEquals(0, bd("4.00").compareTo(s.mttrHours()));
    }

    @Test
    void anAssetStillBrokenHasNoMttr() {
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(failure(pump, 10, null, null, false)), T0, day(100), 10).get(0);
        assertNull(s.mttrHours());
    }

    @Test
    void downtimeLongerThanThePeriodNeverGoesNegative() {
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(failure(pump, 1, 999_999, null, true)), T0, day(2), 10).get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(s.mtbfHours()));
    }

    @Test
    void repeatFailuresAreThoseWithin30DaysOfThePrevious() {
        // days 1, 20 (repeat), 80 (not), 100 (repeat)
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(
                failure(pump, 100, null, null, true), failure(pump, 1, null, null, true),
                failure(pump, 80, null, null, true), failure(pump, 20, null, null, true)), T0, day(200), 10).get(0);
        assertEquals(4, s.failures());
        assertEquals(2, s.repeatFailures());
    }

    @Test
    void topFailureCauseIgnoresCaseAndBlanks() {
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(
                failure(pump, 1, null, "Worn seal", true), failure(pump, 2, null, " worn SEAL ", true),
                failure(pump, 3, null, "Power surge", true), failure(pump, 4, null, "  ", true), failure(pump, 5, null, null, true)),
                T0, day(100), 10).get(0);
        assertEquals("Worn seal", s.topFailureCause());
    }

    @Test
    void worstAssetsComeFirstAndTheListIsCapped() {
        List<Job> jobs = List.of(failure(pump, 1, null, null, true), failure(lift, 1, 30, null, true),
                failure(lift, 5, 30, null, true), failure(pump, 9, 90, null, true), failure(UUID.randomUUID(), 3, null, null, true));
        List<AssetStats> out = MaintenanceAnalytics.assetReliability(jobs, T0, day(100), 2);
        assertEquals(2, out.size());
        assertEquals(pump, out.get(0).assetId()); // 2 failures, 90 min downtime beats lift's 60
        assertEquals(lift, out.get(1).assetId());
    }

    @Test
    void costOfFailuresIsSummed() {
        AssetStats s = MaintenanceAnalytics.assetReliability(List.of(failure(pump, 1, null, null, true), failure(pump, 2, null, null, true)), T0, day(10), 5).get(0);
        assertEquals(0, bd("200.00").compareTo(s.totalCost()));
    }

    @Test
    void anEmptyPeriodIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MaintenanceAnalytics.assetReliability(List.of(), T0, T0, 5));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @Test
    void percentAndComplianceHandleZero() {
        assertNull(MaintenanceAnalytics.percent(1, 0));
        assertNull(MaintenanceAnalytics.compliance(0, 0));
        assertEquals(0, bd("66.67").compareTo(MaintenanceAnalytics.compliance(2, 1)));
    }
}
