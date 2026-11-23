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

    /** "Someone else could do this step" means someone else holds the permission the endpoint demands. */
    static final String PAYROLL_APPROVE_PERMISSION = "SECURITY_PAYROLL_APPROVE";
    static final String PAYROLL_PAY_PERMISSION     = "SECURITY_PAYROLL_PAY";

    private final PayrollPeriodRepository   periodRepository;
    private final PayrollLineItemRepository lineItemRepository;
    private final ShiftRepository           shiftRepository;
    private final GuardRepository           guardRepository;
    private final GradeRateRepository       gradeRateRepository;
    private final BranchRepository          branchRepository;

    /** Optional, so existing constructor callers (tests) keep working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.SeparationOfDutiesChecker separationOfDuties;
    private final SiteRepository            siteRepository;

    /** The company's calendar. Optional so a hand-built service still works, on the South African default. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    /** Sunday and public-holiday pay. Optional, so a hand-built service pays the ordinary way; the company must also have switched it on. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PayPremiumService premiums;

    private java.time.ZoneId zone(TenantId tenantId) {
        return za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId);
    }
    // FIX: closes the confirmed AuditEvent coverage gap for Payroll —
    // approving a period is a major financial event that previously
    // had no generic audit trail entry at all.
    private final za.co.handyflow.platform.security.domain.repository.AuditEventRepository auditRepository;

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

        // Maker-checker: whoever created the period does not approve it, unless nobody else could (sole operator,
        // which is allowed and recorded for the owner/auditor).
        if (separationOfDuties != null) {
            separationOfDuties.require(tenantId, PAYROLL_APPROVE_PERMISSION, period.getCreatedBy(), approvedBy,
                    "You created this payroll period, so someone else must approve it",
                    "SECURITY_PAYROLL_PERIOD", id, "Created and approved the same payroll period");
        }

        List<PayrollLineItem> items = computeLineItems(tenantId, period);
        try {
            lineItemRepository.saveAll(items);
            lineItemRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // The unique index (V840) caught a shift that was paid by another approval running at the same moment.
            throw new HandyFlowException("A shift in this period has just been paid in another period. Reload and approve again.",
                    HttpStatus.CONFLICT, "SHIFT_ALREADY_PAID");
        }

        BigDecimal totalHours = items.stream()
                .map(li -> li.getHoursWorked().add(li.getOvertimeHours()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCents = items.stream().mapToLong(PayrollLineItem::getGrossAmountCents).sum();

        period.approve(approvedBy, totalHours, totalCents);
        periodRepository.save(period);

        writeAudit(tenantId, approvedBy, id, "APPROVED",
                "{\"lineItemCount\":" + items.size() + ",\"totalCents\":" + totalCents + "}");

        log.info("[Payroll] Period approved id={} lines={} totalCents={}", id, items.size(), totalCents);
        return toResponse(period, items.size(), totalCents);
    }

    // ── Line item computation ──────────────────────────────────────────────────

    private List<PayrollLineItem> computeLineItems(TenantId tenantId, PayrollPeriod period) {
        // The company's days, not UTC days (audit 1.6).
        java.time.ZoneId zone = zone(tenantId);
        Instant from = PayrollRules.windowStart(period.getPeriodStart(), zone);
        Instant to   = PayrollRules.windowEnd(period.getPeriodEnd(), zone);

        List<Shift> shifts = shiftRepository.findByTenantInRange(tenantId, from, to);
        if (period.getBranchId() != null) {
            // A branch period pays only shifts at that branch's sites (audit 1.6: it used to pay every shift of the tenant).
            Set<UUID> branchSites = new HashSet<>(siteRepository.findIdsByBranch(tenantId, period.getBranchId()));
            shifts = shifts.stream().filter(s -> branchSites.contains(s.getSiteId())).toList();
        }

        // A shift still running when the period is approved would never be paid: its pay would fall into no later period, because it started before that
        // period does. Make the supervisor finish or close it first.
        long running = shifts.stream().filter(s -> s.getStatus().name().equals("ACTIVE")).count();
        if (running > 0) {
            throw new HandyFlowException(running + " shift(s) in this period are still running. Complete or close them, then approve again.",
                    HttpStatus.CONFLICT, "SHIFTS_STILL_RUNNING");
        }

        List<Shift> completedShifts = withoutPaidShifts(tenantId, shifts.stream()
                .filter(s -> s.getStatus().name().equals("COMPLETED"))
                .toList());

        List<PayrollLineItem> items = new ArrayList<>();
        PayPremiumService.Context premium = premiums == null ? null : premiums.context(tenantId);

        for (Shift shift : completedShifts) {
            // Deleted guards are included: pay already earned is still owed (audit 1.6: they used to be skipped and vanish from payroll).
            Guard guard = guardRepository.findAnyById(tenantId, shift.getGuardId()).orElseThrow(() ->
                    new HandyFlowException("A completed shift belongs to a guard that no longer exists (shift " + shift.getId() + "). Contact support before approving.",
                            HttpStatus.UNPROCESSABLE_ENTITY, "GUARD_NOT_FOUND"));

            int rateCents = resolveRate(tenantId, guard, period.getPeriodEnd());
            int standardHours = resolveStandardHours(tenantId, guard, period.getPeriodEnd());

            // A shift with Sunday or public-holiday hours is paid in parts; every other shift, and every shift while the premium is off, is paid as before.
            List<PayPremiumRules.Line> parts = premium == null ? null
                    : PayPremiumRules.lines(shift.getStartAt(), shift.getEndAt(), zone, standardHours, rateCents, premium.settings(), premium.holidays());
            if (parts != null) {
                addPremiumLines(items, tenantId, period, guard, shift, rateCents, parts);
                continue;
            }

            PayrollRules.Split split = PayrollRules.split(shift.getStartAt(), shift.getEndAt(), standardHours);
            BigDecimal regularHours  = split.regular();
            BigDecimal overtimeHours = split.overtime();

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

    private static void addPremiumLines(List<PayrollLineItem> items, TenantId tenantId, PayrollPeriod period, Guard guard, Shift shift,
                                        int rateCents, List<PayPremiumRules.Line> parts) {
        for (PayPremiumRules.Line l : parts) {
            switch (l.type()) {
                case "REGULAR" -> items.add(PayrollLineItem.regular(tenantId, period.getId(), guard.getId(),
                        shift.getId(), shift.getStartAt(), shift.getEndAt(), l.hours(), rateCents));
                case "OVERTIME" -> items.add(PayrollLineItem.overtime(tenantId, period.getId(), guard.getId(),
                        shift.getId(), shift.getStartAt(), shift.getEndAt(), l.hours(), rateCents));
                default -> items.add(PayrollLineItem.premium(tenantId, period.getId(), guard.getId(), shift.getId(), shift.getStartAt(), shift.getEndAt(),
                        PayrollLineItem.LineType.valueOf(l.type()), l.hours(), rateCents, l.payRateCents()));
            }
        }
    }

    /** Drops shifts that already have pay in any period, so a shift can never be paid twice (audit 1.6). Queried in batches to keep the IN list small. */
    private List<Shift> withoutPaidShifts(TenantId tenantId, List<Shift> completed) {
        if (completed.isEmpty()) return completed;
        Set<UUID> paid = new HashSet<>();
        for (int i = 0; i < completed.size(); i += 500) {
            List<UUID> batch = completed.subList(i, Math.min(i + 500, completed.size())).stream().map(Shift::getId).toList();
            paid.addAll(lineItemRepository.findPaidShiftIds(tenantId, batch));
        }
        return completed.stream().filter(s -> !paid.contains(s.getId())).toList();
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
                    .orElse(PayrollRules.DEFAULT_STANDARD_HOURS);
        }
        return PayrollRules.DEFAULT_STANDARD_HOURS;
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
        java.time.ZoneId csvZone = zone(tenantId);

        for (PayrollLineItem li : items) {
            // Every line is written, also for a guard record that has since been deleted (audit 1.6: such rows used to vanish from the file).
            Guard guard = guardCache.computeIfAbsent(li.getGuardId(),
                    gid -> guardRepository.findAnyById(tenantId, gid).orElse(null));

            String shiftDate = PayrollRules.shiftDate(li.getShiftStartAt(), csvZone).format(dateFmt);

            // Point decimals whatever the server locale; text quoted and defused against spreadsheet formulas.
            csv.append(String.join(",",
                    PayrollCsv.text(period.getName()), period.getPeriodStart().toString(), period.getPeriodEnd().toString(),
                    PayrollCsv.text(guard != null ? guard.getFullName() : "(unknown guard)"), PayrollCsv.text(guard != null ? nvl(guard.getGrade()) : ""),
                    PayrollCsv.text(guard != null ? nvl(guard.getPsiraNumber()) : ""),
                    li.getLineType().name(), shiftDate,
                    PayrollCsv.hours(li.getHoursWorked()), PayrollCsv.hours(li.getOvertimeHours()),
                    String.valueOf(li.getHourlyRateCents()), String.valueOf(li.getOvertimeRateCents()),
                    String.valueOf(li.getGrossAmountCents()), PayrollCsv.money(li.getGrossAmountCents()))).append("\n");
        }

        // A repeat download of an already exported period must work: only the first export changes the status.
        if (period.isApproved()) {
            period.markExported("CSV");
            periodRepository.save(period);
        }

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
                    Guard g = guardRepository.findAnyById(tenantId, e.getKey()).orElse(null);
                    List<PayrollLineItem> gItems = e.getValue();

                    BigDecimal totalHours = gItems.stream()
                            .map(li -> li.getHoursWorked().add(li.getOvertimeHours()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long totalCents = gItems.stream().mapToLong(PayrollLineItem::getGrossAmountCents).sum();

                    return new PayrollExportJsonResponse.GuardPaySummary(
                            e.getKey(), g != null ? g.getFullName() : "(unknown guard)", g != null ? g.getGrade() : null, g != null ? g.getPsiraNumber() : null,
                            gItems.size(), totalHours, totalCents, totalCents / 100.0);
                })
                .sorted(Comparator.comparing(PayrollExportJsonResponse.GuardPaySummary::guardName))
                .toList();

        if (period.isApproved()) {
            period.markExported("JSON");
            periodRepository.save(period);
        }

        return new PayrollExportJsonResponse(
                period.getId(), period.getName(),
                period.getPeriodStart().toString(), period.getPeriodEnd().toString(),
                items.size(), period.getTotalHours(), period.getTotalAmountCents(),
                period.getTotalAmountCents() != null ? period.getTotalAmountCents() / 100.0 : 0.0,
                summaries);
    }

    @Transactional
    public PayrollPeriodResponse markPaid(TenantId tenantId, UUID id, UUID actorId) {
        PayrollPeriod period = findPeriod(tenantId, id);
        // Maker-checker: the person who approved the run does not also release payment.
        if (separationOfDuties != null) {
            separationOfDuties.require(tenantId, PAYROLL_PAY_PERMISSION, period.getApprovedBy(), actorId,
                    "You approved this payroll period, so someone else must mark it paid",
                    "SECURITY_PAYROLL_PERIOD", id, "Approved and marked paid the same payroll period");
        }
        period.markPaid();
        periodRepository.save(period);
        writeAudit(tenantId, actorId, id, "MARKED_PAID", null);
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

    // FIX: closes the confirmed AuditEvent coverage gap for Payroll.
    // Mirrors ShiftService.writeAudit()'s own established helper
    // pattern exactly, entityType "PAYROLL_PERIOD".
    private void writeAudit(TenantId tenantId, UUID actorId, UUID periodId, String action, String metadataJson) {
        auditRepository.save(za.co.handyflow.platform.security.domain.model.AuditEvent.record(
                tenantId, actorId,
                za.co.handyflow.platform.security.domain.model.AuditEvent.ActorType.USER,
                "PAYROLL_PERIOD", periodId, action,
                null, null, metadataJson));
    }
}