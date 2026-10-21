package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOvertimeClaimRepository;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.Day;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.Timesheet;
import za.co.handyflow.platform.hr.dto.OvertimeDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Overtime claims: worked out from the attendance record at the Act's rates, approved or rejected by someone other than the
 * employee, and picked up by payroll once approved.
 * <p>
 * Employees earning above the BCEA earnings threshold are outside the Act's overtime rates, so no claim is made for them:
 * their overtime is whatever their contract says. Work on public holidays is also not claimed here; what is due under s 18 is
 * for HR to decide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeService {

    private final HrOvertimeClaimRepository claimRepo;
    private final HrEmployeeRepository employeeRepo;
    private final AttendanceService attendance;
    private final EmployeeViewService viewService;

    // ── Claiming ────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public RequestResult request(TenantId tenantId, RangeRequest req, UUID actor, Set<String> held) {
        if (req.employeeId() == null) throw new IllegalArgumentException("Choose the employee.");
        HrEmployee emp = employee(tenantId, req.employeeId());
        requireMayRequest(tenantId, emp, held, actor);
        Timesheet ts = attendance.timesheetFor(tenantId, emp, req.from(), req.to());
        WorkingTimeRules.Values rules = WorkingTimeRules.on(req.to());
        BigDecimal hourly = AttendanceRules.hourlyRate(emp.getGrossSalary(), ts.weeklyMinutes());
        LocalDate today = LocalDate.now(AttendanceRules.ZONE);

        int created = 0, updated = 0, withdrawn = 0;
        List<Skipped> skipped = new ArrayList<>();
        Map<LocalDate, HrOvertimeClaim> existing = claimRepo.forEmployee(tenantId, emp.getId(), req.from(), req.to()).stream()
                .collect(Collectors.toMap(HrOvertimeClaim::getWorkDate, c -> c));
        List<HrOvertimeClaim> touched = new ArrayList<>();

        for (Day d : ts.days()) {
            HrOvertimeClaim claim = existing.get(d.date());
            boolean hasOvertime = d.overtimeKind() != null && d.overtimeMinutes() > 0;
            if (!hasOvertime) {
                if (claim != null && OvertimeRules.PENDING.equals(claim.getStatus())) { claim.withdraw(); claimRepo.save(claim); withdrawn++; }
                continue;
            }
            String problem = OvertimeRules.claimProblem(d.overtimeKind(), d.overtimeMinutes(), d.date(), today, ts.aboveThreshold(),
                    AttendanceRules.INCOMPLETE.equals(d.status()));
            if (problem != null) { skipped.add(new Skipped(d.date(), problem)); continue; }
            if (hourly.signum() <= 0) { skipped.add(new Skipped(d.date(), "The employee has no salary on record, so there is nothing to base the rate on.")); continue; }

            BigDecimal multiplier = AttendanceRules.multiplier(d.overtimeKind(), rules);
            BigDecimal amount = AttendanceRules.overtimePay(hourly, d.overtimeMinutes(), multiplier);
            OvertimeRules.Action action = OvertimeRules.onRequest(claim == null ? null : claim.getStatus(), claim == null ? 0 : claim.getMinutes(), d.overtimeMinutes());
            if (action == OvertimeRules.Action.CREATE) {
                touched.add(claimRepo.save(HrOvertimeClaim.of(tenantId.getValue(), emp.getId(), d.date(), d.overtimeKind(), d.overtimeMinutes(), multiplier, hourly, amount, actor)));
                created++;
            } else if (action == OvertimeRules.Action.UPDATE) {
                claim.reprice(d.overtimeKind(), d.overtimeMinutes(), multiplier, hourly, amount);
                touched.add(claimRepo.save(claim));
                updated++;
            } else if (claim != null) touched.add(claim);
        }
        log.info("Overtime request employee={} created={} updated={} withdrawn={} skipped={}", emp.getEmployeeNumber(), created, updated, withdrawn, skipped.size());
        return new RequestResult(created, updated, withdrawn, skipped, touched.stream().map(c -> view(c, emp, canDecide(held))).toList());
    }

    // ── Reading ─────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Claim> list(TenantId tenantId, String status, Set<String> held, UUID actor) {
        Collection<String> statuses = status == null || status.isBlank()
                ? List.of(OvertimeRules.PENDING, OvertimeRules.APPROVED, OvertimeRules.REJECTED, OvertimeRules.WITHDRAWN) : List.of(status.toUpperCase());
        List<HrOvertimeClaim> all = claimRepo.byStatus(tenantId, statuses);
        if (HrAccess.teamOnly(held)) {
            Set<UUID> team = viewService.teamOf(tenantId, actor);
            all = all.stream().filter(c -> team.contains(c.getEmployeeId())).toList();
        } else if (!HrAccess.seesAll(held) && !canDecide(held)) {
            throw new AccessDeniedException("You do not have access to everyone's overtime. Use My overtime.");
        }
        return views(tenantId, all, held);
    }

    @Transactional(readOnly = true)
    public List<Claim> mine(TenantId tenantId, UUID userId, LocalDate from, LocalDate to) {
        if (userId == null) throw new AccessDeniedException("Your user could not be identified.");
        HrEmployee emp = employeeRepo.findByUserId(tenantId, userId).orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        return claimRepo.forEmployee(tenantId, emp.getId(), from, to).stream()
                .sorted(Comparator.comparing(HrOvertimeClaim::getWorkDate).reversed()).map(c -> view(c, emp, false)).toList();
    }

    // ── Deciding ────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public Claim approve(TenantId tenantId, UUID id, String note, UUID actor, Set<String> held) {
        return decide(tenantId, id, true, note, actor, held);
    }

    /** Rejects a waiting claim, or takes back an approved one that no pay run has taken yet. */
    @Transactional
    public Claim reject(TenantId tenantId, UUID id, String note, UUID actor, Set<String> held) {
        return decide(tenantId, id, false, note, actor, held);
    }

    private Claim decide(TenantId tenantId, UUID id, boolean approving, String note, UUID actor, Set<String> held) {
        if (!canDecide(held)) throw new AccessDeniedException("You need the overtime approval permission for this.");
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this decision cannot be recorded.");
        HrOvertimeClaim c = claim(tenantId, id);
        HrEmployee emp = employee(tenantId, c.getEmployeeId());
        String problem = OvertimeRules.decisionProblem(c.getStatus(), approving, note, c.getPayRunId() != null, actor.equals(emp.getUserId()));
        if (problem != null) throw problem.startsWith("You cannot") ? new AccessDeniedException(problem)
                : problem.startsWith("Give") || problem.startsWith("Keep") ? new IllegalArgumentException(problem) : new IllegalStateException(problem);
        if (approving) c.approve(actor, note); else c.reject(actor, note);
        return view(claimRepo.save(c), emp, true);
    }

    @Transactional
    public Claim withdraw(TenantId tenantId, UUID id, UUID actor, Set<String> held) {
        HrOvertimeClaim c = claim(tenantId, id);
        HrEmployee emp = employee(tenantId, c.getEmployeeId());
        boolean own = actor != null && actor.equals(emp.getUserId());
        if (!own && !AttendanceService.canManage(held)) throw new AccessDeniedException("Only the employee or HR can withdraw this claim.");
        String problem = OvertimeRules.withdrawProblem(c.getStatus());
        if (problem != null) throw new IllegalStateException(problem);
        c.withdraw();
        return view(claimRepo.save(c), emp, false);
    }

    // ── Payroll ─────────────────────────────────────────────────────────────────────────────────

    /**
     * The approved overtime a pay run takes, per employee: everything worked up to the end of the period that no other run has
     * taken. Anything this run took before is given back first, so calculating a run again gives the same answer.
     */
    @Transactional
    public Map<UUID, List<HrOvertimeClaim>> takeFor(TenantId tenantId, UUID payRunId, LocalDate periodEnd) {
        for (HrOvertimeClaim c : claimRepo.takenBy(tenantId, payRunId)) { c.linkTo(null); claimRepo.save(c); }
        Map<UUID, List<HrOvertimeClaim>> out = new HashMap<>();
        for (HrOvertimeClaim c : claimRepo.payableIn(tenantId, periodEnd, payRunId)) out.computeIfAbsent(c.getEmployeeId(), k -> new ArrayList<>()).add(c);
        return out;
    }

    /** Claims worked up to the end of a period that are still waiting for a decision. */
    @Transactional(readOnly = true)
    public int waitingCount(TenantId tenantId, LocalDate periodEnd) {
        return (int) claimRepo.byStatus(tenantId, List.of(OvertimeRules.PENDING)).stream().filter(c -> !c.getWorkDate().isAfter(periodEnd)).count();
    }

    /** Marks the claims a run has paid, so they cannot be changed or taken twice. */
    @Transactional
    public void markTaken(Collection<HrOvertimeClaim> claims, UUID payRunId) {
        for (HrOvertimeClaim c : claims) { c.linkTo(payRunId); claimRepo.save(c); }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private List<Claim> views(TenantId tenantId, List<HrOvertimeClaim> claims, Set<String> held) {
        if (claims.isEmpty()) return List.of();
        Map<UUID, HrEmployee> emps = employeeRepo.findAllById(claims.stream().map(HrOvertimeClaim::getEmployeeId).distinct().toList()).stream()
                .collect(Collectors.toMap(HrEmployee::getId, e -> e));
        boolean decide = canDecide(held);
        return claims.stream().filter(c -> emps.containsKey(c.getEmployeeId())).map(c -> view(c, emps.get(c.getEmployeeId()), decide)).toList();
    }

    private static Claim view(HrOvertimeClaim c, HrEmployee e, boolean decide) {
        return new Claim(c.getId(), e.getId(), e.getEmployeeNumber(), e.getFullName(), c.getWorkDate(), c.getKind(), c.getMinutes(), c.getMultiplier(),
                c.getHourlyRate(), c.getAmount(), c.getStatus(), c.getDecidedAt(), c.getDecisionNote(), c.getPayRunId(),
                decide && c.getPayRunId() == null && (OvertimeRules.PENDING.equals(c.getStatus()) || OvertimeRules.APPROVED.equals(c.getStatus())));
    }

    static boolean canDecide(Set<String> held) { return held.contains("HR_OVERTIME_APPROVE") || held.contains("HR_MANAGE"); }

    private void requireMayRequest(TenantId tenantId, HrEmployee emp, Set<String> held, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified.");
        if (actor.equals(emp.getUserId()) || AttendanceService.canManage(held)) return;
        if (held.contains("HR_TEAM_READ") && viewService.teamOf(tenantId, actor).contains(emp.getId())) return;
        throw new AccessDeniedException("You can claim overtime for yourself, or for your team.");
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private HrOvertimeClaim claim(TenantId tenantId, UUID id) {
        return claimRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("OvertimeClaim", id.toString()));
    }
}
