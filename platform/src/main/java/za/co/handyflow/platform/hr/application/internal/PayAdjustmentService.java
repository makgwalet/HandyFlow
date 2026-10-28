package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPayAdjustment;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.NoticePayRules;
import za.co.handyflow.platform.hr.domain.model.SeveranceRules;
import za.co.handyflow.platform.hr.domain.model.NoticeRules;
import za.co.handyflow.platform.hr.domain.model.PayAdjustmentRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayAdjustmentRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bonuses and back pay. Whoever may prepare payroll enters one, because it changes what the next run pays and taxes; nobody may enter one for
 * themselves. A pay run takes every open item dated up to the end of its period, and gives them back if it is recalculated or cancelled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayAdjustmentService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrPayAdjustmentRepository repo;
    private final HrEmployeeRepository employeeRepo;
    private final HrPayRunRepository payRunRepo;

    // ── Entering and cancelling ────────────────────────────────────────────

    @Transactional
    public View add(TenantId tenantId, UUID employeeId, AddRequest req, UUID actor) {
        return add(tenantId, employeeId, req, actor, null);
    }

    /** As {@link #add}, for a correction of an issued payslip: the item remembers which one. */
    @Transactional
    public View add(TenantId tenantId, UUID employeeId, AddRequest req, UUID actor, UUID correctsPayslipId) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employee(tenantId, employeeId);
        String problem = PayAdjustmentRules.addProblem(req.kind(), req.amount(), req.payFrom(), req.description(), req.reason(),
                req.relatesFrom(), req.relatesTo(), LocalDate.now(ZONE), emp.getStartDate(), actor.equals(emp.getUserId()));
        if (problem != null) {
            if (problem.startsWith("You cannot")) throw new AccessDeniedException(problem);
            throw new IllegalArgumentException(problem);
        }
        if (PayAdjustmentRules.SEVERANCE.equals(req.kind()))
            throw new IllegalArgumentException("Severance needs a SARS tax directive. Enter it from the severance panel of the employee who was retrenched.");
        if (PayAdjustmentRules.NOTICE_PAY.equals(req.kind())) {
            boolean already = repo.forEmployee(tenantId, employeeId).stream().anyMatch(i -> PayAdjustmentRules.NOTICE_PAY.equals(i.getKind()) && PayAdjustmentRules.OPEN.equals(i.getStatus()));
            String notice = NoticePayRules.problem(emp.getStatus(), emp.getEndType(), req.amount(),
                    NoticeRules.owedPay(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate()),
                    NoticeRules.fullPay(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate()), already);
            if (notice != null) throw new IllegalArgumentException(notice);
        }
        HrPayAdjustment item = HrPayAdjustment.of(tenantId.getValue(), employeeId, req.kind(), req.amount().setScale(2, RoundingMode.HALF_UP),
                req.payFrom(), req.description(), req.relatesFrom(), req.relatesTo(), req.reason(), actor);
        item.correcting(correctsPayslipId);
        HrPayAdjustment saved = repo.save(item);
        log.info("Pay item {} {} entered for employee={} by user={}", saved.getKind(), saved.getAmount(), employeeId, actor);
        return view(saved, emp, null);
    }

    /**
     * Severance for someone retrenched: the amount, the SARS directive number and the tax the directive asks to be withheld. The
     * amount may not be below the BCEA minimum unless the employee unreasonably refused alternative work.
     */
    @Transactional
    public View addSeverance(TenantId tenantId, UUID employeeId, SeveranceRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employee(tenantId, employeeId);
        if (actor.equals(emp.getUserId())) throw new AccessDeniedException("You cannot enter a payment for yourself. Ask another payroll user.");
        if (emp.getEndDate() == null) throw new IllegalArgumentException("Severance is entered once employment has ended.");
        if (req.reason() == null || req.reason().isBlank()) throw new IllegalArgumentException("Give the reason for the payment.");
        if (req.reason().trim().length() > 300) throw new IllegalArgumentException("The reason is too long (300 characters at most).");
        boolean already = repo.forEmployee(tenantId, employeeId).stream().anyMatch(i -> PayAdjustmentRules.SEVERANCE.equals(i.getKind()) && PayAdjustmentRules.OPEN.equals(i.getStatus()));
        String problem = SeveranceRules.problem(emp.getStatus(), emp.getEndType(), req.amount(), req.directiveNumber(), req.directiveTax(),
                req.refusedAlternative(), SeveranceRules.statutoryMinimum(emp.getGrossSalary(), emp.getStartDate(), emp.getEndDate()), already);
        if (problem != null) throw new IllegalArgumentException(problem);
        HrPayAdjustment item = HrPayAdjustment.severance(tenantId.getValue(), employeeId, req.amount().setScale(2, RoundingMode.HALF_UP), emp.getEndDate(),
                req.reason(), req.directiveNumber(), req.directiveTax().setScale(2, RoundingMode.HALF_UP), req.refusedAlternative(), actor);
        HrPayAdjustment saved = repo.save(item);
        log.info("Severance {} (directive tax {}) entered for employee={} by user={}", saved.getAmount(), saved.getDirectiveTax(), employeeId, actor);
        return view(saved, emp, null);
    }

    public record SeveranceRequest(BigDecimal amount, String directiveNumber, BigDecimal directiveTax, boolean refusedAlternative, String reason) {}

    @Transactional
    public View cancel(TenantId tenantId, UUID id, CancelRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrPayAdjustment a = repo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("PayAdjustment", id.toString()));
        String problem = PayAdjustmentRules.cancelProblem(a.getStatus(), a.getPayRunId() != null, req == null ? null : req.reason());
        if (problem != null) throw problem.startsWith("Give") || problem.startsWith("The reason") ? new IllegalArgumentException(problem) : new IllegalStateException(problem);
        a.cancel(actor, req.reason());
        return view(repo.save(a), employee(tenantId, a.getEmployeeId()), null);
    }

    // ── Reading ────────────────────────────────────────────────────────────

    /** Newest first. A blank status means all of them. */
    @Transactional(readOnly = true)
    public List<View> list(TenantId tenantId, String status) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return views(tenantId, repo.byStatus(tenantId, s));
    }

    @Transactional(readOnly = true)
    public List<View> forEmployee(TenantId tenantId, UUID employeeId) {
        employee(tenantId, employeeId);
        return views(tenantId, repo.forEmployee(tenantId, employeeId));
    }

    /** Corrections entered against one issued payslip, newest first. */
    @Transactional(readOnly = true)
    public List<View> forPayslip(TenantId tenantId, UUID payslipId) {
        return views(tenantId, repo.forPayslip(tenantId, payslipId));
    }

    /** Whether a correction of this kind is already waiting (not cancelled) for the payslip. */
    @Transactional(readOnly = true)
    public boolean correctionWaiting(TenantId tenantId, UUID payslipId, String kind) {
        return repo.forPayslip(tenantId, payslipId).stream().anyMatch(a -> kind.equals(a.getKind()) && PayAdjustmentRules.OPEN.equals(a.getStatus()));
    }

    // ── Payroll ────────────────────────────────────────────────────────────

    /**
     * The open items a pay run takes, per employee: everything dated up to the end of the period that no other run has taken. Anything this
     * run took before is given back first, so calculating a run again gives the same answer.
     */
    @Transactional
    public Map<UUID, List<HrPayAdjustment>> takeFor(TenantId tenantId, UUID payRunId, LocalDate periodEnd) {
        for (HrPayAdjustment a : repo.takenBy(tenantId, payRunId)) { a.linkTo(null); repo.save(a); }
        Map<UUID, List<HrPayAdjustment>> out = new HashMap<>();
        for (HrPayAdjustment a : repo.payableIn(tenantId, periodEnd, payRunId)) out.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a);
        return out;
    }

    /** Marks the items a run has paid, so they cannot be cancelled or taken twice. */
    @Transactional
    public void markTaken(Collection<HrPayAdjustment> items, UUID payRunId) {
        for (HrPayAdjustment a : items) { a.linkTo(payRunId); repo.save(a); }
    }

    /** Gives back every item a run had taken, for a run that will never be paid. Returns how many. */
    @Transactional
    public int releaseFor(TenantId tenantId, UUID payRunId) {
        List<HrPayAdjustment> taken = repo.takenBy(tenantId, payRunId);
        for (HrPayAdjustment a : taken) { a.linkTo(null); repo.save(a); }
        return taken.size();
    }

    /** The tax the SARS directives ask to be withheld on the severance items. */
    public static BigDecimal severanceTax(Collection<HrPayAdjustment> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (HrPayAdjustment a : items) if (PayAdjustmentRules.SEVERANCE.equals(a.getKind()) && a.getDirectiveTax() != null) sum = sum.add(a.getDirectiveTax());
        return sum;
    }

    /** The directive numbers of the severance items, comma separated; null when there are none. */
    public static String severanceDirectives(Collection<HrPayAdjustment> items) {
        String s = items.stream().filter(a -> PayAdjustmentRules.SEVERANCE.equals(a.getKind()) && a.getDirectiveNumber() != null)
                .map(HrPayAdjustment::getDirectiveNumber).collect(java.util.stream.Collectors.joining(", "));
        return s.isEmpty() ? null : s;
    }

    public static BigDecimal total(Collection<HrPayAdjustment> items, String kind) {
        BigDecimal sum = BigDecimal.ZERO;
        for (HrPayAdjustment a : items) if (kind.equals(a.getKind())) sum = sum.add(a.getAmount());
        return sum;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<View> views(TenantId tenantId, List<HrPayAdjustment> items) {
        if (items.isEmpty()) return List.of();
        Map<UUID, HrEmployee> emps = employeeRepo.findAllById(items.stream().map(HrPayAdjustment::getEmployeeId).distinct().toList()).stream()
                .collect(Collectors.toMap(HrEmployee::getId, e -> e));
        Map<UUID, String> runs = new HashMap<>();
        List<UUID> runIds = items.stream().map(HrPayAdjustment::getPayRunId).filter(Objects::nonNull).distinct().toList();
        if (!runIds.isEmpty()) for (HrPayRun r : payRunRepo.findAllById(runIds)) runs.put(r.getId(), r.getPayRunNumber());
        return items.stream().filter(a -> emps.containsKey(a.getEmployeeId()))
                .map(a -> view(a, emps.get(a.getEmployeeId()), a.getPayRunId() == null ? null : runs.get(a.getPayRunId()))).toList();
    }

    private static View view(HrPayAdjustment a, HrEmployee e, String runNumber) {
        return new View(a.getId(), e.getId(), e.getEmployeeNumber(), e.getFullName(), a.getKind(), PayAdjustmentRules.label(a.getKind()), a.getAmount(),
                a.getPayFrom(), a.getDescription(), a.getRelatesFrom(), a.getRelatesTo(), a.getReason(), a.getStatus(), a.getPayRunId(), runNumber,
                PayAdjustmentRules.OPEN.equals(a.getStatus()) && a.getPayRunId() == null, a.getCancelReason(), a.getCreatedAt());
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }
}
