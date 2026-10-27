package za.co.handyflow.platform.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Vendor-performance and asset-reliability arithmetic, shared by {@code facilities} (in-house) and
 * {@code facilitiesmanagement} (provider) so both modules compute these figures one way. Pure functions over
 * {@link Job} samples: no persistence, no clock, no tenant — callers decide which jobs go in.
 * <p>
 * Definitions:
 * <ul>
 *   <li><b>Failure</b>: a reactive job (anything except planned/inspection work) that was not cancelled.</li>
 *   <li><b>MTTR</b> (hours): average of created→completed for completed reactive jobs.</li>
 *   <li><b>MTBF</b> (hours): (period length − recorded downtime) ÷ failures, never below zero; absent when there
 *       were no failures. It is an operating-time estimate for the reporting period, not a lifetime figure.</li>
 *   <li><b>Repeat failure</b>: a failure on the same asset within {@value #REPEAT_WINDOW_DAYS} days of the
 *       previous one.</li>
 *   <li><b>Rework rate</b>: completed jobs that were sent back at least once ÷ completed jobs.</li>
 *   <li><b>SLA compliance</b>: met ÷ (met + breached); absent when nothing has concluded.</li>
 * </ul>
 */
public final class MaintenanceAnalytics {

    public static final int REPEAT_WINDOW_DAYS = 30;

    private MaintenanceAnalytics() {}

    /**
     * One work order reduced to what the analytics need.
     * {@code resolutionMet}: TRUE met, FALSE breached, null when there is no SLA or it has not concluded.
     */
    public record Job(UUID assetId, UUID vendorId, String vendorName, boolean reactive, boolean cancelled,
                      boolean completed, Instant createdAt, Instant completedAt, Integer downtimeMinutes,
                      String failureCause, int reworkCount, BigDecimal cost, Boolean resolutionMet) {}

    public record VendorStats(UUID vendorId, String vendorName, long jobs, long completed, long cancelled, long open,
                              BigDecimal avgResolutionHours, long reworkJobs, BigDecimal reworkRatePercent,
                              long slaMet, long slaBreached, BigDecimal slaCompliancePercent,
                              BigDecimal totalCost, BigDecimal avgCost) {}

    public record AssetStats(UUID assetId, long failures, long repeatFailures, long downtimeMinutes,
                             BigDecimal mttrHours, BigDecimal mtbfHours, String topFailureCause, BigDecimal totalCost) {}

    // ── Vendors ──────────────────────────────────────────────────────────────

    /** One row per vendor (jobs without a vendor are ignored), busiest first. */
    public static List<VendorStats> vendorPerformance(Collection<Job> jobs) {
        Map<UUID, List<Job>> byVendor = new LinkedHashMap<>();
        for (Job j : jobs) {
            if (j.vendorId() == null) continue;
            byVendor.computeIfAbsent(j.vendorId(), k -> new ArrayList<>()).add(j);
        }
        List<VendorStats> out = new ArrayList<>();
        for (Map.Entry<UUID, List<Job>> e : byVendor.entrySet()) {
            List<Job> list = e.getValue();
            long cancelled = list.stream().filter(Job::cancelled).count();
            List<Job> done = list.stream().filter(j -> !j.cancelled() && j.completed()).toList();
            long open = list.size() - cancelled - done.size();

            BigDecimal avgHours = average(done.stream().filter(j -> j.completedAt() != null && j.createdAt() != null)
                    .map(j -> hours(j.createdAt(), j.completedAt())).toList());
            long reworkJobs = done.stream().filter(j -> j.reworkCount() > 0).count();
            long met = list.stream().filter(j -> !j.cancelled() && Boolean.TRUE.equals(j.resolutionMet())).count();
            long breached = list.stream().filter(j -> !j.cancelled() && Boolean.FALSE.equals(j.resolutionMet())).count();
            BigDecimal totalCost = done.stream().map(j -> nz(j.cost())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgCost = done.isEmpty() ? null
                    : totalCost.divide(BigDecimal.valueOf(done.size()), 2, RoundingMode.HALF_UP);

            out.add(new VendorStats(e.getKey(), latestName(list), list.size(), done.size(), cancelled, open, avgHours,
                    reworkJobs, percent(reworkJobs, done.size()), met, breached, compliance(met, breached),
                    totalCost, avgCost));
        }
        out.sort(Comparator.comparingLong(VendorStats::jobs).reversed()
                .thenComparing(v -> v.vendorName() == null ? "" : v.vendorName().toLowerCase()));
        return out;
    }

    // ── Assets ───────────────────────────────────────────────────────────────

    /**
     * Worst assets first (most failures, then most downtime), at most {@code limit}. Assets with no failures in
     * the period are not listed.
     */
    public static List<AssetStats> assetReliability(Collection<Job> jobs, Instant periodStart, Instant periodEnd, int limit) {
        if (!periodEnd.isAfter(periodStart)) throw new IllegalArgumentException("The reporting period must be longer than zero");
        double periodHours = hours(periodStart, periodEnd).doubleValue();

        Map<UUID, List<Job>> byAsset = new LinkedHashMap<>();
        for (Job j : jobs) {
            if (j.assetId() == null || !j.reactive() || j.cancelled()) continue;
            byAsset.computeIfAbsent(j.assetId(), k -> new ArrayList<>()).add(j);
        }

        List<AssetStats> out = new ArrayList<>();
        for (Map.Entry<UUID, List<Job>> e : byAsset.entrySet()) {
            List<Job> failures = new ArrayList<>(e.getValue());
            failures.sort(Comparator.comparing(Job::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));

            long repeats = 0;
            for (int i = 1; i < failures.size(); i++) {
                Instant prev = failures.get(i - 1).createdAt();
                Instant cur = failures.get(i).createdAt();
                if (prev != null && cur != null && Duration.between(prev, cur).toDays() < REPEAT_WINDOW_DAYS
                        && !cur.isBefore(prev)) repeats++;
            }
            long downtime = failures.stream().mapToLong(j -> j.downtimeMinutes() == null ? 0 : Math.max(0, j.downtimeMinutes())).sum();
            BigDecimal mttr = average(failures.stream()
                    .filter(j -> j.completed() && j.completedAt() != null && j.createdAt() != null)
                    .map(j -> hours(j.createdAt(), j.completedAt())).toList());
            double operating = Math.max(0, periodHours - downtime / 60.0);
            BigDecimal mtbf = BigDecimal.valueOf(operating / failures.size()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = failures.stream().map(j -> nz(j.cost())).reduce(BigDecimal.ZERO, BigDecimal::add);

            out.add(new AssetStats(e.getKey(), failures.size(), repeats, downtime, mttr, mtbf, topCause(failures), cost));
        }
        out.sort(Comparator.comparingLong(AssetStats::failures).reversed()
                .thenComparing(Comparator.comparingLong(AssetStats::downtimeMinutes).reversed())
                .thenComparing(AssetStats::assetId));
        return out.size() > limit ? new ArrayList<>(out.subList(0, Math.max(0, limit))) : out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** met ÷ (met + breached) as a percentage, or null when nothing has concluded. */
    public static BigDecimal compliance(long met, long breached) {
        return percent(met, met + breached);
    }

    public static BigDecimal percent(long part, long whole) {
        if (whole <= 0) return null;
        return BigDecimal.valueOf(part).multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hours(Instant from, Instant to) {
        long seconds = Math.max(0, Duration.between(from, to).getSeconds());
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** The vendor's name on its most recent job (names are snapshotted on the work order and can be renamed). */
    private static String latestName(List<Job> jobs) {
        return jobs.stream()
                .filter(j -> j.vendorName() != null && !j.vendorName().isBlank())
                .max(Comparator.comparing(Job::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Job::vendorName).orElse(null);
    }

    /** Most frequent failure cause (case-insensitive, trimmed); ties go to the alphabetically first. */
    private static String topCause(List<Job> failures) {
        Map<String, Long> counts = new TreeMap<>();
        Map<String, String> display = new HashMap<>();
        for (Job j : failures) {
            if (j.failureCause() == null || j.failureCause().isBlank()) continue;
            String key = j.failureCause().trim().toLowerCase();
            counts.merge(key, 1L, Long::sum);
            display.putIfAbsent(key, j.failureCause().trim());
        }
        return counts.entrySet().stream().max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(en -> display.get(en.getKey())).orElse(null);
    }
}
