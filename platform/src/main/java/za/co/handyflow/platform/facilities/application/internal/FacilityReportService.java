package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityComplianceCertificateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityPpmScheduleRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityWorkOrderRepository;
import za.co.handyflow.platform.facilities.dto.FacilityAssetReliabilityResponse;
import za.co.handyflow.platform.facilities.dto.FacilityDashboardResponse;
import za.co.handyflow.platform.facilities.dto.FacilityVendorPerformanceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.MaintenanceAnalytics;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Management reporting for the in-house facilities function: a live dashboard, contractor performance and asset
 * reliability. Computed on request from the work orders themselves, inside the caller's {@link AccessScope}; the
 * contractor and asset reports are planner/manager views, so callers limited to their own jobs or their own requests
 * are refused (403 {@code SCOPE_RESTRICTED}), as they are for the per-asset history. The arithmetic is shared with the
 * facilities-management provider module through {@link MaintenanceAnalytics}.
 * <p>
 * Reports load the matching work orders into memory, so periods are capped at {@value #MAX_SPAN_DAYS} days.
 */
@Service
@RequiredArgsConstructor
public class FacilityReportService {

    static final long MAX_SPAN_DAYS = 366;
    static final int DEFAULT_ASSET_LIMIT = 20;
    static final int MAX_ASSET_LIMIT = 100;
    static final int EXPIRY_WARNING_DAYS = 30;

    private static final List<String> OPEN_STATUSES = List.of("OPEN", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION");
    private static final List<String> PRIORITIES = List.of("LOW", "NORMAL", "HIGH", "URGENT", "EMERGENCY");

    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityAccessPolicy accessPolicy;

    // ── Dashboard ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FacilityDashboardResponse dashboard(TenantId tenantId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        LocalDate today = FacilityTime.today();

        List<FacilityWorkOrder> active = workOrderRepository.findOpenInScope(tenantId, scope.allSites(), scope.siteIds(),
                scope.allJobs(), scope.technicianIds(), scope.allRequesters(), scope.requesterUserId());
        long awaitingApproval = 0, overdue = 0;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        OPEN_STATUSES.forEach(s -> byStatus.put(s, 0L));
        Map<String, Long> byPriority = new LinkedHashMap<>();
        PRIORITIES.forEach(p -> byPriority.put(p, 0L));
        for (FacilityWorkOrder w : active) {
            if ("REQUESTED".equals(w.getStatus())) { awaitingApproval++; continue; }
            byStatus.merge(w.getStatus(), 1L, Long::sum);
            byPriority.merge(w.getPriority(), 1L, Long::sum);
            if (w.isOverdue(today)) overdue++;
        }
        long open = byStatus.values().stream().mapToLong(Long::longValue).sum();

        Instant since = today.minusDays(30).atStartOfDay(FacilityTime.ZONE).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(FacilityTime.ZONE).toInstant();
        List<FacilityWorkOrder> done = workOrderRepository.findCompletedBetweenInScope(tenantId, since, until,
                scope.allSites(), scope.siteIds(), scope.allJobs(), scope.technicianIds(), scope.allRequesters(), scope.requesterUserId());
        BigDecimal totalHours = BigDecimal.ZERO;
        int timed = 0;
        long reworked = 0, downtime = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (FacilityWorkOrder w : done) {
            if (w.getCreatedAt() != null && w.getCompletedAt() != null) {
                totalHours = totalHours.add(BigDecimal.valueOf(Math.max(0, Duration.between(w.getCreatedAt(), w.getCompletedAt()).getSeconds()))
                        .divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP));
                timed++;
            }
            if (w.getReworkCount() > 0) reworked++;
            if (w.getDowntimeMinutes() != null && w.getDowntimeMinutes() > 0) downtime += w.getDowntimeMinutes();
            if (w.getCost() != null) cost = cost.add(w.getCost());
        }
        BigDecimal avgHours = timed == 0 ? null : totalHours.divide(BigDecimal.valueOf(timed), 2, RoundingMode.HALF_UP);

        long ppmOverdue = ppmScheduleRepository.countOverdueInScope(tenantId, today, scope.allSites(), scope.siteIds());
        long expiring = certificateRepository.countExpiringInScope(tenantId, today, today.plusDays(EXPIRY_WARNING_DAYS),
                scope.allSites(), scope.siteIds());
        long lapsed = certificateRepository.countLapsedInScope(tenantId, today, scope.allSites(), scope.siteIds());

        return new FacilityDashboardResponse(Instant.now(), open, byStatus.get("OPEN"), byStatus.get("ON_HOLD"), overdue,
                awaitingApproval, byStatus.get("PENDING_VERIFICATION"), byStatus, byPriority, done.size(), avgHours,
                MaintenanceAnalytics.percent(reworked, done.size()), downtime, cost, ppmOverdue, expiring, lapsed);
    }

    // ── Contractor performance ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FacilityVendorPerformanceResponse vendorPerformance(TenantId tenantId, LocalDate from, LocalDate to) {
        AccessScope scope = requireManagementScope(tenantId);
        validatePeriod(from, to);

        List<MaintenanceAnalytics.Job> jobs = createdBetween(tenantId, scope, from, to).stream()
                .filter(w -> w.getVendorId() != null).map(FacilityReportService::toJob).toList();
        List<FacilityVendorPerformanceResponse.Row> rows = MaintenanceAnalytics.vendorPerformance(jobs).stream()
                .map(v -> new FacilityVendorPerformanceResponse.Row(v.vendorId(), v.vendorName(), v.jobs(), v.completed(),
                        v.cancelled(), v.open(), v.avgResolutionHours(), v.reworkJobs(), v.reworkRatePercent(),
                        v.totalCost(), v.avgCost()))
                .toList();
        return new FacilityVendorPerformanceResponse(from, to, rows);
    }

    // ── Asset reliability ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FacilityAssetReliabilityResponse assetReliability(TenantId tenantId, LocalDate from, LocalDate to, Integer limit) {
        AccessScope scope = requireManagementScope(tenantId);
        validatePeriod(from, to);
        int cap = limit == null ? DEFAULT_ASSET_LIMIT : Math.max(1, Math.min(MAX_ASSET_LIMIT, limit));

        Instant now = Instant.now();
        Instant start = from.atStartOfDay(FacilityTime.ZONE).toInstant();
        if (!start.isBefore(now)) throw new IllegalArgumentException("'from' must be in the past");
        Instant end = Collections.min(List.of(to.plusDays(1).atStartOfDay(FacilityTime.ZONE).toInstant(), now));

        List<MaintenanceAnalytics.Job> jobs = createdBetween(tenantId, scope, from, to).stream()
                .filter(w -> w.getAssetId() != null).map(FacilityReportService::toJob).toList();
        List<MaintenanceAnalytics.AssetStats> stats = MaintenanceAnalytics.assetReliability(jobs, start, end, cap);

        Map<UUID, FacilityAsset> assets = stats.isEmpty() ? Map.of() : assetRepository.findAllById(
                        stats.stream().map(MaintenanceAnalytics.AssetStats::assetId).toList())
                .stream().collect(Collectors.toMap(FacilityAsset::getId, a -> a, (a, b) -> a));

        List<FacilityAssetReliabilityResponse.Row> rows = stats.stream().map(s -> {
            FacilityAsset a = assets.get(s.assetId());
            return new FacilityAssetReliabilityResponse.Row(s.assetId(), a != null ? a.getAssetTag() : null,
                    a != null ? a.getName() : null, a != null ? a.getSiteId() : null, s.failures(), s.repeatFailures(),
                    s.downtimeMinutes(), s.mttrHours(), s.mtbfHours(), s.topFailureCause(), s.totalCost());
        }).toList();
        return new FacilityAssetReliabilityResponse(from, to, cap, rows);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AccessScope requireManagementScope(TenantId tenantId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (scope.assignedOnly() || scope.requesterOnly()) {
            throw new HandyFlowException("Not permitted for this access level", HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        }
        return scope;
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("'from' and 'to' are required");
        if (to.isBefore(from)) throw new IllegalArgumentException("'to' must not be before 'from'");
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS)
            throw new IllegalArgumentException("The reporting period cannot exceed " + MAX_SPAN_DAYS + " days");
    }

    private List<FacilityWorkOrder> createdBetween(TenantId tenantId, AccessScope scope, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(FacilityTime.ZONE).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(FacilityTime.ZONE).toInstant();
        return workOrderRepository.findCreatedBetweenInScope(tenantId, start, endExclusive, scope.allSites(), scope.siteIds(),
                scope.allJobs(), scope.technicianIds(), scope.allRequesters(), scope.requesterUserId());
    }

    private static MaintenanceAnalytics.Job toJob(FacilityWorkOrder w) {
        boolean reactive = !"PPM".equals(w.getCategory()) && !"INSPECTION".equals(w.getCategory());
        return new MaintenanceAnalytics.Job(w.getAssetId(), w.getVendorId(), w.getVendorName(), reactive,
                "CANCELLED".equals(w.getStatus()), "COMPLETED".equals(w.getStatus()), w.getCreatedAt(), w.getCompletedAt(),
                w.getDowntimeMinutes(), w.getFailureCause(), w.getReworkCount(), w.getCost(), null);
    }
}
