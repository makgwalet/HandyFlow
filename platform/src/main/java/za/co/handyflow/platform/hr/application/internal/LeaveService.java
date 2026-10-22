package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.hr.dto.LeavePolicyDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.PublicHolidayRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Leave as a ledger. Balances are the sum of dated, append-only movements: accruals earned under the BCEA rules in
 * {@link LeaveRules}, annual leave carried forward, adjustments by HR, days held for requests waiting for approval and
 * days taken. A request holds its days when it is made, releases them if it is rejected or cancelled, and turns them
 * into days taken when it is approved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final BigDecimal EPS = new BigDecimal("0.005");
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrLeaveLedgerRepository ledgerRepo;
    private final HrLeaveRequestRepository requestRepo;
    private final HrLeaveSettingsRepository settingsRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrPayrollSettingsRepository payrollSettingsRepo;
    private final PublicHolidayRepository holidayRepo;
    private final EmployeeViewService viewService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final HrLeavePolicyRepository policyRepo;

    // ── Reading ─────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public List<LeaveBalanceResponse> balances(TenantId tenantId, UUID employeeId, int year) {
        HrEmployee emp = employee(tenantId, employeeId);
        LocalDate today = LocalDate.now();
        ensureAccrued(tenantId, emp, today);
        return balancesFor(tenantId, emp, year, today);
    }

    @Transactional
    public LeaveStatementResponse statement(TenantId tenantId, UUID employeeId, int year) {
        HrEmployee emp = employee(tenantId, employeeId);
        LocalDate today = LocalDate.now();
        ensureAccrued(tenantId, emp, today);
        List<LeaveBalanceResponse> balances = balancesFor(tenantId, emp, year, today);
        List<LeaveStatementResponse.Entry> entries = ledgerRepo.between(tenantId, employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(l -> new LeaveStatementResponse.Entry(l.getId(), l.getLeaveType(), l.getEntryDate(), l.getKind(),
                        l.getDays(), l.getNote(), l.getRequestId(), l.getCreatedBy(), l.getCreatedAt())).toList();
        return new LeaveStatementResponse(employeeId, emp.getFullName(), year, balances, entries);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> requests(TenantId tenantId, String status, Pageable pageable) {
        Map<UUID, String> names = new HashMap<>();
        return requestRepo.findAllByTenant(tenantId, status, pageable).map(r -> toResponse(r, names.computeIfAbsent(r.getEmployeeId(), id -> nameOf(tenantId, id))));
    }

    // ── Requesting ──────────────────────────────────────────────────────────────────────────────

    @Transactional
    public LeaveRequestResponse submit(TenantId tenantId, UUID employeeId, SubmitLeaveRequest req) {
        HrEmployee emp = employee(tenantId, employeeId);
        LocalDate today = LocalDate.now();
        boolean half = Boolean.TRUE.equals(req.halfDay());
        String type = req.leaveType() == null ? null : req.leaveType().trim().toUpperCase();

        Set<LocalDate> holidays = req.startDate() != null && req.endDate() != null && !req.endDate().isBefore(req.startDate()) && req.endDate().isBefore(req.startDate().plusDays(400))
                ? new HashSet<>(holidayRepo.datesBetween(req.startDate(), req.endDate())) : Set.of();
        List<LocalDate> days = LeaveRules.workingDays(req.startDate(), req.endDate(), holidays);
        fail(LeaveRules.requestProblem(type, req.startDate(), req.endDate(), half, days.size(), emp.getStartDate(), emp.getStatus(), today));

        if (!requestRepo.overlapping(tenantId, employeeId, req.startDate(), req.endDate()).isEmpty()) {
            throw new IllegalStateException("This overlaps leave that is already requested or approved for this employee.");
        }

        ensureAccrued(tenantId, emp, today);
        SortedMap<LocalDate, BigDecimal> buckets = buckets(type, emp, days, half);
        checkBalance(tenantId, emp, type, buckets);

        BigDecimal total = LeaveRules.totalDays(days, half);
        HrLeaveRequest request = HrLeaveRequest.create(tenantId, employeeId, type, req.startDate(), req.endDate(), total, req.reason());
        request.setHalfDay(half);
        request.setMedicalCertificate(Boolean.TRUE.equals(req.medicalCertificate()));
        requestRepo.save(request);
        for (var b : buckets.entrySet()) {
            post(tenantId, employeeId, type, b.getKey(), LeaveRules.RESERVED, b.getValue().negate(), request.getId(),
                    "res:" + request.getId() + ":" + b.getKey(), "Held for a request waiting for approval", null);
        }
        log.info("Leave request {} days {} for employee={}", total, type, emp.getEmployeeNumber());
        notifyApprover(tenantId, emp, request);
        return toResponse(request, emp.getFullName());
    }

    // ── Deciding ────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public LeaveRequestResponse approve(TenantId tenantId, UUID requestId, UUID actor, Set<String> held) {
        HrLeaveRequest req = request(tenantId, requestId);
        if (!"PENDING".equals(req.getStatus())) throw new IllegalStateException("Only a request that is waiting can be approved.");
        checkMayDecide(tenantId, req, actor, held);

        req.approve(actor);
        requestRepo.save(req);
        for (HrLeaveLedger r : reservations(tenantId, requestId)) {
            BigDecimal d = r.getDays().negate();
            post(tenantId, req.getEmployeeId(), req.getLeaveType(), r.getEntryDate(), LeaveRules.RELEASED, d, requestId, "rel:" + r.getId(), "Approved", actor);
            post(tenantId, req.getEmployeeId(), req.getLeaveType(), r.getEntryDate(), LeaveRules.TAKEN, d.negate(), requestId,
                    "tak:" + requestId + ":" + r.getEntryDate(), "Leave approved", actor);
        }
        employeeRepo.findActiveById(tenantId, req.getEmployeeId()).ifPresent(emp ->
                notifyEmployee(tenantId, emp, req, NotificationType.LEAVE_REQUEST_APPROVED, "Leave request approved",
                        "Your " + req.getLeaveType().toLowerCase() + " leave request (" + req.getStartDate() + " to " + req.getEndDate() + ") has been approved."));
        return toResponse(req, nameOf(tenantId, req.getEmployeeId()));
    }

    @Transactional
    public LeaveRequestResponse reject(TenantId tenantId, UUID requestId, UUID actor, String reason, Set<String> held) {
        HrLeaveRequest req = request(tenantId, requestId);
        if (!"PENDING".equals(req.getStatus())) throw new IllegalStateException("Only a request that is waiting can be rejected.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for rejecting the request.");
        checkMayDecide(tenantId, req, actor, held);

        req.reject(actor, reason.trim());
        requestRepo.save(req);
        release(tenantId, req, actor, "Rejected");
        employeeRepo.findActiveById(tenantId, req.getEmployeeId()).ifPresent(emp ->
                notifyEmployee(tenantId, emp, req, NotificationType.LEAVE_REQUEST_REJECTED, "Leave request rejected",
                        "Your " + req.getLeaveType().toLowerCase() + " leave request (" + req.getStartDate() + " to " + req.getEndDate()
                                + ") was rejected. Reason: " + reason.trim()));
        return toResponse(req, nameOf(tenantId, req.getEmployeeId()));
    }

    /**
     * Cancels a request. A waiting request can be cancelled by the employee it belongs to or an approver. Approved leave
     * that has not started can be cancelled the same way; leave that has started or finished only by someone who may
     * adjust balances.
     */
    @Transactional
    public LeaveRequestResponse cancel(TenantId tenantId, UUID requestId, UUID actor, String reason, Set<String> held) {
        HrLeaveRequest req = request(tenantId, requestId);
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        boolean owner = employeeRepo.findByUserId(tenantId, actor).map(e -> e.getId().equals(req.getEmployeeId())).orElse(false);
        boolean approver = held.contains("LEAVE_APPROVE") || held.contains("HR_MANAGE");
        boolean adjuster = held.contains("LEAVE_ADJUST");
        LocalDate today = LocalDate.now();

        if ("PENDING".equals(req.getStatus())) {
            if (!owner && !approver) throw new AccessDeniedException("Only the employee or an approver can cancel this request.");
            req.cancel(actor, reason);
            requestRepo.save(req);
            release(tenantId, req, actor, "Cancelled");
        } else if ("APPROVED".equals(req.getStatus())) {
            boolean notStarted = req.getStartDate().isAfter(today);
            if (!(notStarted ? (owner || approver || adjuster) : adjuster))
                throw new AccessDeniedException(notStarted ? "Only the employee or an approver can cancel this leave."
                        : "Leave that has already started can only be cancelled by someone who may adjust balances.");
            if (!notStarted && (reason == null || reason.isBlank())) throw new IllegalArgumentException("Give a reason for cancelling leave that has already started.");
            req.cancel(actor, reason);
            requestRepo.save(req);
            for (HrLeaveLedger t : ledgerRepo.forRequest(tenantId, requestId)) {
                if (!LeaveRules.TAKEN.equals(t.getKind())) continue;
                post(tenantId, req.getEmployeeId(), req.getLeaveType(), t.getEntryDate(), LeaveRules.REVERSAL, t.getDays().negate(), requestId,
                        "rev:" + t.getId(), "Leave cancelled", actor);
            }
        } else {
            throw new IllegalStateException("This request is already " + req.getStatus().toLowerCase() + ".");
        }
        return toResponse(req, nameOf(tenantId, req.getEmployeeId()));
    }

    /** HR adjustment of a balance, dated, signed and explained. */
    @Transactional
    public LeaveStatementResponse.Entry adjust(TenantId tenantId, UUID employeeId, LeaveAdjustmentRequest req, UUID actor) {
        HrEmployee emp = employee(tenantId, employeeId);
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (!LeaveRules.isKnown(req.leaveType())) throw new IllegalArgumentException("Choose a leave type.");
        if (req.days() == null || req.days().signum() == 0) throw new IllegalArgumentException("Enter the number of days to add or take off.");
        if (req.days().abs().compareTo(new BigDecimal("365")) > 0) throw new IllegalArgumentException("That is too many days for one adjustment.");
        if (req.reason() == null || req.reason().isBlank()) throw new IllegalArgumentException("Give the reason for the adjustment.");
        LocalDate date = req.entryDate() != null ? req.entryDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) throw new IllegalArgumentException("An adjustment cannot be dated in the future.");
        if (date.getYear() < LocalDate.now().getYear() - 1) throw new IllegalArgumentException("An adjustment cannot be dated before last year.");
        ensureAccrued(tenantId, emp, LocalDate.now());
        HrLeaveLedger l = ledgerRepo.save(HrLeaveLedger.of(tenantId.getValue(), employeeId, req.leaveType(), date, LeaveRules.ADJUSTMENT,
                req.days().setScale(2, RoundingMode.HALF_UP), null, null, req.reason(), actor));
        return new LeaveStatementResponse.Entry(l.getId(), l.getLeaveType(), l.getEntryDate(), l.getKind(), l.getDays(), l.getNote(),
                null, l.getCreatedBy(), l.getCreatedAt());
    }

    // ── Carry-over policy ───────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PolicyView policy(TenantId tenantId) {
        return policyRepo.findById(tenantId.getValue()).map(LeaveService::view)
                .orElseGet(() -> new PolicyView(null, null, null, null));
    }

    /** Sets or removes the limit on carrying annual leave into a new year. Only rollovers that have not happened yet are affected. */
    @Transactional
    public PolicyView updatePolicy(TenantId tenantId, PolicyRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        fail(LeavePolicyRules.policyProblem(req.carryOverCapDays(), req.effectiveYear(), req.agreementRef(), LocalDate.now(ZONE).getYear()));
        HrLeavePolicy p = policyRepo.findById(tenantId.getValue()).orElseGet(() -> HrLeavePolicy.none(tenantId.getValue()));
        p.set(req.carryOverCapDays() == null ? null : req.carryOverCapDays().setScale(2, RoundingMode.HALF_UP), req.effectiveYear(), req.agreementRef(), actor);
        policyRepo.save(p);
        return view(p);
    }

    /** Who would lose annual leave at the coming rollover if they took no more. Empty when there is no limit that will apply. */
    @Transactional
    public AtRiskReport atRisk(TenantId tenantId) {
        LocalDate today = LocalDate.now(ZONE);
        int next = today.getYear() + 1;
        HrLeavePolicy policy = policyRepo.findById(tenantId.getValue()).orElse(null);
        BigDecimal cap = policy == null ? null : LeavePolicyRules.capFor(policy.getCarryOverCapDays(), policy.getEffectiveYear(), next);
        if (cap == null) return new AtRiskReport(null, next, List.of());
        List<AtRisk> out = new ArrayList<>();
        for (HrEmployee emp : employeeRepo.findNotTerminated(tenantId)) {
            ensureAccrued(tenantId, emp, today);
            BigDecimal available = totalsAt(tenantId, emp, LeaveRules.ANNUAL, today).available();
            BigDecimal ahead = LeaveRules.annualEarned(emp.getStartDate(), ledgerStart(tenantId), today.getYear(), LocalDate.of(today.getYear(), 12, 31))
                    .subtract(LeaveRules.annualEarned(emp.getStartDate(), ledgerStart(tenantId), today.getYear(), today)).max(BigDecimal.ZERO);
            BigDecimal lose = LeavePolicyRules.atRisk(available, ahead, cap);
            if (lose.signum() > 0) out.add(new AtRisk(emp.getId(), emp.getFullName(), available, available.add(ahead), lose));
        }
        out.sort(Comparator.comparing(AtRisk::wouldLose).reversed());
        return new AtRiskReport(cap, next, out);
    }

    private static PolicyView view(HrLeavePolicy p) {
        return new PolicyView(p.getCarryOverCapDays(), p.getEffectiveYear(), p.getAgreementRef(), p.getUpdatedAt());
    }

    // ── Accrual ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Posts what the employee has earned since the last time, and carries annual leave into each new year. Safe to call
     * any number of times: it posts only the difference, and each posting has a key the database accepts once.
     */
    void ensureAccrued(TenantId tenantId, HrEmployee emp, LocalDate asOf) {
        LocalDate ledgerStart = ledgerStart(tenantId);
        LocalDate start = emp.getStartDate();
        UUID id = emp.getId();
        int firstAccrualYear = Math.max(start.getYear(), ledgerStart.getYear());
        int firstYear = ledgerRepo.firstEntry(tenantId, id, LeaveRules.ANNUAL).map(d -> Math.min(d.getYear(), firstAccrualYear)).orElse(firstAccrualYear);

        for (int y = firstYear; y <= asOf.getYear(); y++) {
            LocalDate yearStart = LocalDate.of(y, 1, 1), yearEnd = LocalDate.of(y, 12, 31);
            LocalDate upTo = asOf.isAfter(yearEnd) ? yearEnd : asOf;
            if (y > firstYear) carryForward(tenantId, id, y);
            accrue(tenantId, id, LeaveRules.ANNUAL, yearStart, yearEnd, LeaveRules.annualEarned(start, ledgerStart, y, upTo), upTo);
            if (y >= ledgerStart.getYear()) {
                accrue(tenantId, id, LeaveRules.FAMILY, yearStart, yearEnd, LeaveRules.familyEarned(start, y, upTo), upTo);
            }
        }
        LeaveRules.Cycle cycle = LeaveRules.sickCycle(start, asOf);
        accrue(tenantId, id, LeaveRules.SICK, cycle.from(), cycle.to(), LeaveRules.sickEarned(start, asOf), asOf);
    }

    /**
     * Annual leave still owing on the day employment ended, in days. Brings the ledger up to that day first (posting nothing if it is already
     * there), then works the balance out from entries dated up to it. See {@link LeaveRules#annualOwing}.
     */
    @Transactional
    public BigDecimal annualLeaveOwing(TenantId tenantId, HrEmployee emp, LocalDate leavingDate) {
        ensureAccrued(tenantId, emp, leavingDate);
        List<LeaveRules.Line> lines = ledgerRepo.window(tenantId, emp.getId(), LeaveRules.ANNUAL, LocalDate.of(leavingDate.getYear(), 1, 1), leavingDate)
                .stream().map(l -> new LeaveRules.Line(l.getKind(), l.getDays())).toList();
        BigDecimal earned = LeaveRules.annualEarned(emp.getStartDate(), ledgerStart(tenantId), leavingDate.getYear(), leavingDate);
        return LeaveRules.annualOwing(lines, earned);
    }

    private void accrue(TenantId tenantId, UUID employeeId, String type, LocalDate from, LocalDate to, BigDecimal earned, LocalDate on) {
        BigDecimal posted = ledgerRepo.window(tenantId, employeeId, type, from, to).stream()
                .filter(l -> LeaveRules.ACCRUAL.equals(l.getKind()) || LeaveRules.OPENING.equals(l.getKind()))
                .map(HrLeaveLedger::getDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = earned.subtract(posted);
        if (delta.compareTo(EPS) <= 0) return;
        post(tenantId, employeeId, type, on, LeaveRules.ACCRUAL, delta.setScale(2, RoundingMode.DOWN), null, "accr:" + on,
                "Earned under the BCEA", null);
    }

    /**
     * Whatever annual leave was left at the end of the year moves into the next, including a negative balance. When the company has a
     * limit that applies to this rollover, days above it are forfeited first: an entry dated the last day of the year that is closing,
     * so that year's statement shows where they went, and only the limit is carried.
     */
    private void carryForward(TenantId tenantId, UUID employeeId, int year) {
        String key = "carry:" + year;
        if (ledgerRepo.countKey(tenantId, employeeId, LeaveRules.ANNUAL, LeaveRules.CARRY_FORWARD, key) > 0) return;
        BigDecimal closing = ledgerRepo.window(tenantId, employeeId, LeaveRules.ANNUAL, LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31)).stream()
                .filter(l -> !isReservation(l.getKind())).map(HrLeaveLedger::getDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        HrLeavePolicy policy = policyRepo.findById(tenantId.getValue()).orElse(null);
        BigDecimal cap = policy == null ? null : LeavePolicyRules.capFor(policy.getCarryOverCapDays(), policy.getEffectiveYear(), year);
        BigDecimal lost = LeavePolicyRules.forfeited(closing, cap);
        if (lost.signum() > 0) {
            post(tenantId, employeeId, LeaveRules.ANNUAL, LocalDate.of(year - 1, 12, 31), LeaveRules.EXPIRY, lost.negate(), null, "expire:" + (year - 1),
                    LeavePolicyRules.forfeitNote(cap, policy.getAgreementRef(), year - 1), null);
            closing = closing.subtract(lost);
        }
        if (closing.abs().compareTo(EPS) <= 0) return;
        post(tenantId, employeeId, LeaveRules.ANNUAL, LocalDate.of(year, 1, 1), LeaveRules.CARRY_FORWARD, closing, null, key,
                "Carried forward from " + (year - 1), null);
    }

    // ── Balances ────────────────────────────────────────────────────────────────────────────────

    private record Totals(BigDecimal entitled, BigDecimal taken, BigDecimal pending) {
        BigDecimal available() { return entitled.subtract(taken).subtract(pending); }
    }

    private Totals totals(List<HrLeaveLedger> entries) {
        BigDecimal entitled = BigDecimal.ZERO, taken = BigDecimal.ZERO, pending = BigDecimal.ZERO;
        for (HrLeaveLedger l : entries) {
            switch (l.getKind()) {
                case LeaveRules.OPENING, LeaveRules.ACCRUAL, LeaveRules.CARRY_FORWARD, LeaveRules.ADJUSTMENT, LeaveRules.EXPIRY -> entitled = entitled.add(l.getDays());
                case LeaveRules.TAKEN, LeaveRules.REVERSAL -> taken = taken.subtract(l.getDays());
                case LeaveRules.RESERVED, LeaveRules.RELEASED -> pending = pending.subtract(l.getDays());
                default -> { }
            }
        }
        return new Totals(entitled, taken, pending);
    }

    private LeaveRules.Cycle windowFor(String type, HrEmployee emp, LocalDate date) {
        if (LeaveRules.SICK.equals(type)) return LeaveRules.sickCycle(emp.getStartDate(), date);
        return new LeaveRules.Cycle(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
    }

    private Totals totalsAt(TenantId tenantId, HrEmployee emp, String type, LocalDate date) {
        LeaveRules.Cycle w = windowFor(type, emp, date);
        return totals(ledgerRepo.window(tenantId, emp.getId(), type, w.from(), w.to()));
    }

    private List<LeaveBalanceResponse> balancesFor(TenantId tenantId, HrEmployee emp, int year, LocalDate today) {
        LocalDate ref = year == today.getYear() ? today : LocalDate.of(year, 12, 31);
        List<LeaveBalanceResponse> out = new ArrayList<>();
        for (String type : LeaveRules.TYPES) {
            Totals t = totalsAt(tenantId, emp, type, ref);
            boolean always = LeaveRules.ANNUAL.equals(type) || LeaveRules.SICK.equals(type) || LeaveRules.FAMILY.equals(type);
            boolean any = t.entitled().signum() != 0 || t.taken().signum() != 0 || t.pending().signum() != 0;
            if (!always && !any) continue;
            UUID id = UUID.nameUUIDFromBytes((emp.getId() + ":" + type + ":" + year).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.add(new LeaveBalanceResponse(id, type, year, t.entitled(), t.taken(), t.pending(), t.available()));
        }
        return out;
    }

    // ── Requests: buckets and balance check ────────────────────────────────────────────────────

    /** The days of a request grouped by the window they are charged to, keyed by the date the entry is posted on. */
    private SortedMap<LocalDate, BigDecimal> buckets(String type, HrEmployee emp, List<LocalDate> days, boolean half) {
        SortedMap<LocalDate, BigDecimal> out = new TreeMap<>();
        if (LeaveRules.SICK.equals(type)) {
            out.put(days.get(0), LeaveRules.totalDays(days, half));
            return out;
        }
        Map<Integer, LocalDate> firstOfYear = new TreeMap<>();
        for (LocalDate d : days) firstOfYear.putIfAbsent(d.getYear(), d);
        LeaveRules.daysByYear(days, half).forEach((year, n) -> out.put(firstOfYear.get(year), n));
        return out;
    }

    private void checkBalance(TenantId tenantId, HrEmployee emp, String type, SortedMap<LocalDate, BigDecimal> buckets) {
        if (!LeaveRules.isCapped(type)) return;
        if (LeaveRules.ANNUAL.equals(type)) {
            // Days after a year end come out of what is carried forward, so the whole request must fit what is left now.
            BigDecimal total = buckets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal available = totalsAt(tenantId, emp, type, buckets.firstKey()).available();
            if (available.compareTo(total) < 0) throw insufficient(type, available, total);
            return;
        }
        for (var b : buckets.entrySet()) {
            BigDecimal available = totalsAt(tenantId, emp, type, b.getKey()).available();
            if (available.compareTo(b.getValue()) < 0) throw insufficient(type, available, b.getValue());
        }
    }

    private static IllegalArgumentException insufficient(String type, BigDecimal available, BigDecimal requested) {
        return new IllegalArgumentException("Not enough " + type.toLowerCase().replace('_', ' ') + " leave. Available: "
                + available.stripTrailingZeros().toPlainString() + " days, requested: " + requested.stripTrailingZeros().toPlainString() + ".");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private List<HrLeaveLedger> reservations(TenantId tenantId, UUID requestId) {
        List<HrLeaveLedger> all = ledgerRepo.forRequest(tenantId, requestId);
        Set<String> released = new HashSet<>();
        for (HrLeaveLedger l : all) if (LeaveRules.RELEASED.equals(l.getKind()) && l.getPeriodKey() != null) released.add(l.getPeriodKey());
        return all.stream().filter(l -> LeaveRules.RESERVED.equals(l.getKind()) && !released.contains("rel:" + l.getId())).toList();
    }

    private void release(TenantId tenantId, HrLeaveRequest req, UUID actor, String why) {
        for (HrLeaveLedger r : reservations(tenantId, req.getId())) {
            post(tenantId, req.getEmployeeId(), req.getLeaveType(), r.getEntryDate(), LeaveRules.RELEASED, r.getDays().negate(), req.getId(), "rel:" + r.getId(), why, actor);
        }
    }

    private void checkMayDecide(TenantId tenantId, HrLeaveRequest req, UUID actor, Set<String> held) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        Optional<HrEmployee> me = employeeRepo.findByUserId(tenantId, actor);
        boolean own = me.map(e -> e.getId().equals(req.getEmployeeId())).orElse(false);
        boolean allowSelf = payrollSettingsRepo.findById(tenantId.getValue()).map(HrPayrollSettings::isAllowSelfApproval).orElse(false);
        if (own && !allowSelf) throw new AccessDeniedException("You cannot decide your own leave request. Ask another approver.");
        if (HrAccess.teamOnly(held) && !viewService.teamOf(tenantId, actor).contains(req.getEmployeeId())) {
            throw new AccessDeniedException("You can only decide leave for the people in your own team.");
        }
    }

    private static boolean isReservation(String kind) { return LeaveRules.RESERVED.equals(kind) || LeaveRules.RELEASED.equals(kind); }

    private void post(TenantId tenantId, UUID employeeId, String type, LocalDate on, String kind, BigDecimal days, UUID requestId,
                      String key, String note, UUID actor) {
        ledgerRepo.save(HrLeaveLedger.of(tenantId.getValue(), employeeId, type, on, kind, days, requestId, key, note, actor));
    }

    private LocalDate ledgerStart(TenantId tenantId) {
        return settingsRepo.findById(tenantId.getValue()).map(HrLeaveSettings::getLedgerStartDate).orElseGet(() -> {
            LocalDate start = LocalDate.of(LocalDate.now().getYear(), 1, 1);
            settingsRepo.save(HrLeaveSettings.startingOn(tenantId.getValue(), start));
            return start;
        });
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private HrLeaveRequest request(TenantId tenantId, UUID id) {
        return requestRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", id.toString()));
    }

    private String nameOf(TenantId tenantId, UUID employeeId) {
        return employeeRepo.findActiveById(tenantId, employeeId).map(HrEmployee::getFullName).orElse("Unknown");
    }

    private LeaveRequestResponse toResponse(HrLeaveRequest r, String employeeName) {
        return new LeaveRequestResponse(r.getId(), r.getEmployeeId(), employeeName, r.getLeaveType(), r.getStartDate(), r.getEndDate(),
                r.getDaysRequested(), r.getReason(), r.getStatus(), r.getRejectionReason(), r.getCreatedAt(), r.isHalfDay(),
                r.isMedicalCertificate(), LeaveRules.certificateExpected(r.getLeaveType(), r.getDaysRequested().setScale(0, RoundingMode.CEILING).intValue()));
    }

    private static void fail(String problem) {
        if (problem != null) throw new IllegalArgumentException(problem);
    }

    // ── Notifications ───────────────────────────────────────────────────────────────────────────

    private void notifyApprover(TenantId tenantId, HrEmployee emp, HrLeaveRequest request) {
        List<Recipient> recipients = null;
        if (emp.getManagerId() != null) {
            recipients = employeeRepo.findActiveById(tenantId, emp.getManagerId())
                    .filter(mgr -> mgr.getEmail() != null && !mgr.getEmail().isBlank())
                    .map(mgr -> List.of(Recipient.external(mgr.getFullName(), mgr.getEmail(), mgr.getPhone())))
                    .orElse(null);
        }
        if (recipients == null || recipients.isEmpty()) recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.LEAVE_REQUEST_SUBMITTED)
                .title("Leave request: " + emp.getFullName())
                .message(emp.getFullName() + " requested " + request.getDaysRequested().stripTrailingZeros().toPlainString() + " day(s) "
                        + request.getLeaveType().toLowerCase() + " leave (" + request.getStartDate() + " to " + request.getEndDate() + ") — needs approval.")
                .actionUrl("/hr/leave-requests")
                .sourceModule("hr")
                .sourceEntityId(request.getId().toString())
                .recipients(recipients)
                .build());
    }

    // HrEmployee is not necessarily a platform user, so its own email and phone are the only contact details available.
    private void notifyEmployee(TenantId tenantId, HrEmployee emp, HrLeaveRequest request, NotificationType type, String title, String message) {
        if (emp.getEmail() == null || emp.getEmail().isBlank()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/hr/leave-requests")
                .sourceModule("hr")
                .sourceEntityId(request.getId().toString())
                .recipient(Recipient.external(emp.getFullName(), emp.getEmail(), emp.getPhone()))
                .build());
    }
}
