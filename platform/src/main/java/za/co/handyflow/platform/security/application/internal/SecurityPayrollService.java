// security/application/internal/PayrollService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PayrollService — payroll period management, line item computation, export.
 *
 * Computation model:
 *   1. When a period is approved, find all COMPLETED shifts whose start_at
 *      falls within period_start..period_end for guards scoped to the period.
 *   2. For each shift, resolve the guard's effective rate (explicit override
 *      on guard.hourly_rate_cents, falling back to GradeRate for their grade).
 *   3. Compute hours_worked from shift duration. If hours_worked exceeds
 *      the grade's standard_hours_per_day, the excess is overtime at 1.5×.
 *   4. Create REGULAR + OVERTIME line items for each qualifying shift.
 *   5. Freeze totals on the period record.
 *
 * Export formats:
 *   CSV — one row per line item, suitable for import into Sage Payroll, VIP
 *         Payroll, or any spreadsheet tool.
 *   JSON — structured array of guard pay summaries, suitable for BI tools or
 *          the client public API.
 *
 * WHY compute at approval time rather than on-demand?
 * Pay runs happen once; if a shift is edited after approval (notes, status
 * correction) it shouldn't silently change what was already approved. The
 * snapshot on the line item (shift_start_at, shift_end_at, hourly_rate_cents)
 * is the source of truth for what was paid, not the current shift record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPayrollService {

    private final PayrollPeriodRepository   periodRepository;
    private final PayrollLineItemRepository lineItemRepository;
    private final ShiftRepository           shiftRepository;
    private final GuardRepository           guardRepository;
    private final GradeRateRepository       gradeRateRepository;
    private final BranchRepository          branchRepository;

    // ── Period CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public PayrollPeriodResponse createPeriod(TenantId tenantId, CreatePayrollPeriodRequest req,
                                              UUID createdBy) {
        if (periodRepository.hasOverlappingPeriod(tenantId, req.periodStart(), req.periodEnd(), req.branchId())) {
            throw new HandyFlowException(
                    "An overlapping payroll period already exists for this date range",
                    HttpStatus.CONFLICT, "OVERLAPPING_PERIOD");
        }

        PayrollPeriod.PeriodType type;
        try { type = PayrollPeriod.PeriodType.valueOf(req.periodType()); }
        catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid periodType: " + req.periodType(),
                    HttpStatus.BAD_REQUEST, "INVALID_PERIOD_TYPE");
        }

        PayrollPeriod period = PayrollPeriod.create(
                tenantId, req.branchId(), req.name(), type,
                req.periodStart(), req.periodEnd(), createdBy);
        periodRepository.save(period);

        log.info("[Payroll] Period created id={} name={} range={}/{}",
                period.getId(), period.getName(), req.periodStart(), req.periodEnd());
        return toResponse(period, 0, 0L);
    }

    @Transactional(readOnly = true)
    public Page<PayrollPeriodResponse> listPeriods(TenantId tenantId, Pageable pageable) {
        return periodRepository.findByTenant(tenantId, pageable)
                .map(p -> toResponse(p,
                        lineItemRepository.findByPeriod(p.getId()).size(),
                        lineItemRepository.sumGrossAmountForPeriod(p.getId())));
    }

    @Transactional(readOnly = true)
    public PayrollPeriodResponse getPeriod(TenantId tenantId, UUID id) {
        PayrollPeriod p = findPeriod(tenantId, id);
        return toResponse(p,
                lineItemRepository.findByPeriod(id).size(),
                lineItemRepository.sumGrossAmountForPeriod(id));
    }

    // ── Approval — computes and freezes line items ─────────────────────────────

    @Transactional
    public PayrollPeriodResponse approvePeriod(TenantId tenantId, UUID id, UUID approvedBy) {
        PayrollPeriod period = findPeriod(tenantId, id);
        if (!period.isDraft()) {
            throw new HandyFlowException(
                    "Period is already " + period.getStatus() + " — cannot re-approve",
                    HttpStatus.CONFLICT, "PERIOD_NOT_DRAFT");
        }

        List<PayrollLineItem> items = computeLineItems(tenantId, period);
        lineItemRepository.saveAll(items);

        BigDecimal totalHours = items.stream()
                .map(li -> li.getHoursWorked().add(li.getOvertimeHours()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCents = items.stream().mapToLong(PayrollLineItem::getGrossAmountCents).sum();

        period.approve(approvedBy, totalHours, totalCents);
        periodRepository.save(period);

        log.info("[Payroll] Period approved id={} lines={} totalCents={}", id, items.size(), totalCents);
        return toResponse(period, items.size(), totalCents);
    }

    // ── Line item computation ──────────────────────────────────────────────────

    private List<PayrollLineItem> computeLineItems(TenantId tenantId, PayrollPeriod period) {
        Instant from = period.getPeriodStart().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to   = period.getPeriodEnd().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Shift> shifts = (period.getBranchId() != null)
                ? shiftRepository.findByTenantInRange(tenantId, from, to).stream()
                .filter(s -> {
                    // filter by branch: sites assigned to this branch only
                    return true; // TODO: join with site.branch_id once SiteRepository has branch queries
                })
                .toList()
                : shiftRepository.findByTenantInRange(tenantId, from, to);

        List<Shift> completedShifts = shifts.stream()
                .filter(s -> s.getStatus().name().equals("COMPLETED"))
                .filter(s -> !lineItemRepository.existsForShift(period.getId(), s.getId()))
                .toList();

        List<PayrollLineItem> items = new ArrayList<>();

        for (Shift shift : completedShifts) {
            Guard guard = guardRepository.findActiveById(tenantId, shift.getGuardId())
                    .orElse(null);
            if (guard == null) continue;

            int rateCents = resolveRate(tenantId, guard, period.getPeriodEnd());
            int standardHours = resolveStandardHours(tenantId, guard, period.getPeriodEnd());

            double durationHours = Duration.between(shift.getStartAt(), shift.getEndAt())
                    .toMinutes() / 60.0;
            BigDecimal totalHours = BigDecimal.valueOf(durationHours).setScale(2, RoundingMode.HALF_UP);
            BigDecimal stdHours   = BigDecimal.valueOf(standardHours);

            BigDecimal regularHours  = totalHours.min(stdHours);
            BigDecimal overtimeHours = totalHours.subtract(regularHours).max(BigDecimal.ZERO);

            items.add(PayrollLineItem.regular(tenantId, period.getId(), guard.getId(),
                    shift.getId(), shift.getStartAt(), shift.getEndAt(), regularHours, rateCents));

            if (overtimeHours.compareTo(BigDecimal.ZERO) > 0) {
                items.add(PayrollLineItem.overtime(tenantId, period.getId(), guard.getId(),
                        shift.getId(), shift.getStartAt(), shift.getEndAt(), overtimeHours, rateCents));
            }
        }

        if (items.isEmpty()) {
            throw new HandyFlowException(
                    "No completed shifts found for this period — ensure shifts are completed and " +
                            "guard rates are configured before approving",
                    HttpStatus.UNPROCESSABLE_ENTITY, "NO_PAYABLE_SHIFTS");
        }

        return items;
    }

    private int resolveRate(TenantId tenantId, Guard guard, LocalDate asOf) {
        // 1. Explicit override on guard record
        if (guard.getHourlyRateCents() != null && guard.getHourlyRateCents() > 0) {
            return guard.getHourlyRateCents();
        }
        // 2. Grade-based rate
        if (guard.getGrade() != null) {
            return gradeRateRepository.findEffectiveRate(tenantId, guard.getGrade(), asOf)
                    .map(GradeRate::getHourlyRateCents)
                    .orElseThrow(() -> new HandyFlowException(
                            "No rate configured for grade " + guard.getGrade() +
                                    " as of " + asOf + ". Set a GradeRate or explicit guard rate before approving.",
                            HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_RATE"));
        }
        throw new HandyFlowException(
                "Guard " + guard.getFullName() + " has no grade and no explicit rate configured",
                HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_RATE");
    }

    private int resolveStandardHours(TenantId tenantId, Guard guard, LocalDate asOf) {
        if (guard.getGrade() != null) {
            return gradeRateRepository.findEffectiveRate(tenantId, guard.getGrade(), asOf)
                    .map(GradeRate::getStandardHoursPerDay)
                    .orElse(9);
        }
        return 9;
    }

    // ── Export ─────────────────────────────────────────────────────────────────

    @Transactional
    public byte[] exportCsv(TenantId tenantId, UUID id) {
        PayrollPeriod period = findPeriod(tenantId, id);
        if (!period.isApproved() && !period.isExported()) {
            throw new HandyFlowException(
                    "Period must be APPROVED before export (current: " + period.getStatus() + ")",
                    HttpStatus.CONFLICT, "PERIOD_NOT_APPROVED");
        }

        List<PayrollLineItem> items = lineItemRepository.findByPeriod(id);
        Map<UUID, Guard> guardCache = new HashMap<>();

        StringBuilder csv = new StringBuilder();
        csv.append("PeriodName,PeriodStart,PeriodEnd,GuardName,GuardGrade,PSiRANumber,");
        csv.append("LineType,ShiftDate,HoursWorked,OvertimeHours,RateCents,OvertimeRateCents,GrossCents,GrossZAR\n");

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (PayrollLineItem li : items) {
            Guard guard = guardCache.computeIfAbsent(li.getGuardId(),
                    gid -> guardRepository.findActiveById(tenantId, gid).orElse(null));
            if (guard == null) continue;

            String shiftDate = li.getShiftStartAt().atOffset(ZoneOffset.UTC)
                    .format(dateFmt);
            double grossZar = li.getGrossAmountCents() / 100.0;

            csv.append(String.format("\"%s\",%s,%s,\"%s\",%s,%s,%s,%s,%.2f,%.2f,%d,%d,%d,%.2f%n",
                    period.getName(), period.getPeriodStart(), period.getPeriodEnd(),
                    guard.getFullName(), nvl(guard.getGrade()), nvl(guard.getPsiraNumber()),
                    li.getLineType().name(), shiftDate,
                    li.getHoursWorked().doubleValue(), li.getOvertimeHours().doubleValue(),
                    li.getHourlyRateCents(), li.getOvertimeRateCents(),
                    li.getGrossAmountCents(), grossZar));
        }

        if (period.isDraft()) { /* already not-approved, handled above */ }
        period.markExported("CSV");
        periodRepository.save(period);

        log.info("[Payroll] Period exported CSV id={} rows={}", id, items.size());
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public PayrollExportJsonResponse exportJson(TenantId tenantId, UUID id) {
        PayrollPeriod period = findPeriod(tenantId, id);
        if (!period.isApproved() && !period.isExported()) {
            throw new HandyFlowException(
                    "Period must be APPROVED before export (current: " + period.getStatus() + ")",
                    HttpStatus.CONFLICT, "PERIOD_NOT_APPROVED");
        }

        List<PayrollLineItem> items = lineItemRepository.findByPeriod(id);

        // Group by guard, build per-guard summary
        Map<UUID, List<PayrollLineItem>> byGuard = items.stream()
                .collect(Collectors.groupingBy(PayrollLineItem::getGuardId));

        List<PayrollExportJsonResponse.GuardPaySummary> summaries = byGuard.entrySet().stream()
                .map(e -> {
                    Guard g = guardRepository.findActiveById(tenantId, e.getKey()).orElse(null);
                    if (g == null) return null;
                    List<PayrollLineItem> gItems = e.getValue();

                    BigDecimal totalHours = gItems.stream()
                            .map(li -> li.getHoursWorked().add(li.getOvertimeHours()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long totalCents = gItems.stream().mapToLong(PayrollLineItem::getGrossAmountCents).sum();

                    return new PayrollExportJsonResponse.GuardPaySummary(
                            g.getId(), g.getFullName(), g.getGrade(), g.getPsiraNumber(),
                            gItems.size(), totalHours, totalCents, totalCents / 100.0);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PayrollExportJsonResponse.GuardPaySummary::guardName))
                .toList();

        period.markExported("JSON");
        periodRepository.save(period);

        return new PayrollExportJsonResponse(
                period.getId(), period.getName(),
                period.getPeriodStart().toString(), period.getPeriodEnd().toString(),
                items.size(), period.getTotalHours(), period.getTotalAmountCents(),
                period.getTotalAmountCents() != null ? period.getTotalAmountCents() / 100.0 : 0.0,
                summaries);
    }

    @Transactional
    public PayrollPeriodResponse markPaid(TenantId tenantId, UUID id) {
        PayrollPeriod period = findPeriod(tenantId, id);
        period.markPaid();
        periodRepository.save(period);
        log.info("[Payroll] Period marked PAID id={}", id);
        return toResponse(period,
                lineItemRepository.findByPeriod(id).size(),
                lineItemRepository.sumGrossAmountForPeriod(id));
    }

    // ── Line item queries ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PayrollLineItem> getLineItems(TenantId tenantId, UUID periodId) {
        findPeriod(tenantId, periodId); // validate ownership
        return lineItemRepository.findByPeriod(periodId);
    }

    // ── Grade rates ────────────────────────────────────────────────────────────

    @Transactional
    public GradeRate setGradeRate(TenantId tenantId, SetGradeRateRequest req, UUID createdBy) {
        GradeRate rate = GradeRate.create(
                tenantId, req.grade(), req.hourlyRateCents(),
                req.standardHoursPerDay() != null ? req.standardHoursPerDay() : 9,
                req.effectiveFrom(), createdBy);
        return gradeRateRepository.save(rate);
    }

    @Transactional(readOnly = true)
    public List<GradeRate> getGradeRates(TenantId tenantId) {
        return gradeRateRepository.findAllByTenant(tenantId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PayrollPeriod findPeriod(TenantId tenantId, UUID id) {
        return periodRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", id.toString()));
    }

    private PayrollPeriodResponse toResponse(PayrollPeriod p, int lineCount, long totalCents) {
        return new PayrollPeriodResponse(
                p.getId(), p.getBranchId(), p.getName(), p.getPeriodType().name(),
                p.getPeriodStart(), p.getPeriodEnd(), p.getStatus().name(),
                p.getTotalHours(), totalCents, totalCents / 100.0,
                lineCount, p.getApprovedBy(), p.getApprovedAt(),
                p.getExportedAt(), p.getExportFormat(), p.getCreatedAt());
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}