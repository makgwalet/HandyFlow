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
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employee(tenantId, employeeId);
        String problem = PayAdjustmentRules.addProblem(req.kind(), req.amount(), req.payFrom(), req.description(), req.reason(),
                req.relatesFrom(), req.relatesTo(), LocalDate.now(ZONE), emp.getStartDate(), actor.equals(emp.getUserId()));
        if (problem != null) {
            if (problem.startsWith("You cannot")) throw new AccessDeniedException(problem);
            throw new IllegalArgumentException(problem);
        }
        if (PayAdjustmentRules.NOTICE_PAY.equals(req.kind())) {
            boolean already = repo.forEmployee(tenantId, employeeId).stream().anyMatch(i -> PayAdjustmentRules.NOTICE_PAY.equals(i.getKind()) && PayAdjustmentRules.OPEN.equals(i.getStatus()));
            String notice = NoticePayRules.problem(emp.getStatus(), emp.getEndType(), req.amount(),
                    NoticeRules.owedPay(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate()),
                    NoticeRules.fullPay(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate()), already);
            if (notice != null) throw new IllegalArgumentException(notice);
        }
        HrPayAdjustment saved = repo.save(HrPayAdjustment.of(tenantId.getValue(), employeeId, req.kind(), req.amount().setScale(2, RoundingMode.HALF_UP),
                req.payFrom(), req.description(), req.relatesFrom(), req.relatesTo(), req.reason(), actor));
        log.info("Pay item {} {} entered for employee={} by user={}", saved.getKind(), saved.getAmount(), employeeId, actor);
        return view(saved, emp, null);
    }

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
