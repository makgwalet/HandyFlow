package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Review cycles. HR sets up a cycle for a period and opens it; every employee who worked in that period gets a review with their manager as
 * reviewer. The cycle can close only when every review has been released or left out with a reason.
 */
@Service
@RequiredArgsConstructor
public class PerfCycleService {

    public static final String A_EDIT = "EDIT", A_LAUNCH = "LAUNCH", A_DELETE = "DELETE", A_CLOSE = "CLOSE", A_ADD = "ADD_REVIEW";

    private final HrPerfCycleRepository cycleRepo;
    private final HrPerfReviewRepository reviewRepo;
    private final HrEmployeeRepository employeeRepo;
    private final PerfSupport support;
    private final PerfReviewViews views;
    private final PerfNotifier notifier;

    @Transactional(readOnly = true)
    public List<CycleRow> cycles(TenantId tenantId, Set<String> held) {
        PerfSupport.requireRead(held);
        List<CycleRow> out = new ArrayList<>();
        for (HrPerfCycle c : cycleRepo.forTenant(tenantId)) out.add(row(c, reviewRepo.forCycle(tenantId, c.getId())));
        return out;
    }

    @Transactional(readOnly = true)
    public CycleDetail cycle(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        PerfSupport.requireRead(held);
        return detail(tenantId, find(tenantId, id), held, userId);
    }

    @Transactional
    public CycleDetail create(TenantId tenantId, CycleRequest req, UUID userId, Set<String> held) {
        PerfSupport.requireManage(held);
        PerfSupport.fail(PerfRules.cycleProblem(req.name(), req.periodStart(), req.periodEnd(), req.selfDue(), req.managerDue()));
        HrPerfCycle c = HrPerfCycle.of(tenantId.getValue(), req.name(), req.periodStart(), req.periodEnd(), req.selfDue(), req.managerDue(), req.include360(), userId);
        cycleRepo.save(c);
        support.event(tenantId, HrPerfEvent.CYCLE, c.getId(), "CREATED", null, userId);
        return detail(tenantId, c, held, userId);
    }

    @Transactional
    public CycleDetail update(TenantId tenantId, UUID id, CycleRequest req, UUID userId, Set<String> held) {
        PerfSupport.requireManage(held);
        HrPerfCycle c = find(tenantId, id);
        if (!PerfRules.DRAFT.equals(c.getStatus())) throw new IllegalStateException("A cycle that has been opened can no longer be changed.");
        PerfSupport.fail(PerfRules.cycleProblem(req.name(), req.periodStart(), req.periodEnd(), req.selfDue(), req.managerDue()));
        c.edit(req.name(), req.periodStart(), req.periodEnd(), req.selfDue(), req.managerDue(), req.include360());
        cycleRepo.save(c);
        support.event(tenantId, HrPerfEvent.CYCLE, c.getId(), "EDITED", null, userId);
        return detail(tenantId, c, held, userId);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, Set<String> held) {
        PerfSupport.requireManage(held);
        HrPerfCycle c = find(tenantId, id);
        if (!PerfRules.DRAFT.equals(c.getStatus())) throw new IllegalStateException("Only a cycle that has not been opened can be deleted.");
        cycleRepo.delete(c);
    }

    /** Opens the cycle and makes a review for everyone who worked in the period. */
    @Transactional
    public CycleDetail launch(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        PerfSupport.requireManage(held);
        HrPerfCycle c = find(tenantId, id);
        if (!PerfRules.DRAFT.equals(c.getStatus())) throw new IllegalStateException("This cycle has already been opened.");
        List<HrEmployee> staff = employeeRepo.findNotTerminated(tenantId);
        Map<UUID, HrEmployee> byId = new HashMap<>();
        for (HrEmployee e : staff) byId.put(e.getId(), e);
        List<HrPerfReview> made = new ArrayList<>();
        for (HrEmployee e : staff) if (inScope(e, c)) made.add(newReview(tenantId, c, e, byId));
        if (made.isEmpty()) throw new IllegalStateException("There is nobody to review in that period.");
        c.launch(userId);
        cycleRepo.save(c);
        for (HrPerfReview r : made) reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.CYCLE, c.getId(), "OPENED", made.size() + (made.size() == 1 ? " review made" : " reviews made"), userId);
        notifier.selfReviewAsked(tenantId, c, made);
        return detail(tenantId, c, held, userId);
    }

    /** A person who joined after the cycle opened. */
    @Transactional
    public CycleDetail addReview(TenantId tenantId, UUID id, AddReviewRequest req, UUID userId, Set<String> held) {
        PerfSupport.requireManage(held);
        HrPerfCycle c = find(tenantId, id);
        if (!PerfRules.OPEN.equals(c.getStatus())) throw new IllegalStateException("Reviews can be added only while the cycle is open.");
        HrEmployee e = support.serving(tenantId, req.employeeId());
        if (!inScope(e, c)) throw new IllegalArgumentException(e.getFullName() + " did not work in that period.");
        for (HrPerfReview r : reviewRepo.forCycle(tenantId, c.getId())) if (r.getEmployeeId().equals(e.getId())) throw new IllegalStateException(e.getFullName() + " already has a review in this cycle.");
        Map<UUID, HrEmployee> byId = new HashMap<>();
        for (HrEmployee x : employeeRepo.findNotTerminated(tenantId)) byId.put(x.getId(), x);
        HrPerfReview r = newReview(tenantId, c, e, byId);
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.CYCLE, c.getId(), "REVIEW_ADDED", e.getFullName(), userId);
        notifier.selfReviewAsked(tenantId, c, List.of(r));
        return detail(tenantId, c, held, userId);
    }

    @Transactional
    public CycleDetail close(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        PerfSupport.requireManage(held);
        HrPerfCycle c = find(tenantId, id);
        if (!PerfRules.OPEN.equals(c.getStatus())) throw new IllegalStateException("Only an open cycle can be closed.");
        long unfinished = reviewRepo.forCycle(tenantId, c.getId()).stream()
                .filter(r -> PerfRules.AWAITING_SELF.equals(r.getStatus()) || PerfRules.AWAITING_MANAGER.equals(r.getStatus()) || PerfRules.SUBMITTED.equals(r.getStatus())).count();
        PerfSupport.fail(PerfRules.closeCycleProblem(unfinished));
        c.close(userId);
        cycleRepo.save(c);
        support.event(tenantId, HrPerfEvent.CYCLE, c.getId(), "CLOSED", null, userId);
        return detail(tenantId, c, held, userId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────

    private HrPerfCycle find(TenantId tenantId, UUID id) {
        return cycleRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Cycle", String.valueOf(id)));
    }

    /** Someone who started on or before the end of the period and had not left before it began. */
    static boolean inScope(HrEmployee e, HrPerfCycle c) {
        return (e.getStartDate() == null || !e.getStartDate().isAfter(c.getPeriodEnd())) && (e.getEndDate() == null || !e.getEndDate().isBefore(c.getPeriodStart()));
    }

    private HrPerfReview newReview(TenantId tenantId, HrPerfCycle c, HrEmployee e, Map<UUID, HrEmployee> staff) {
        UUID reviewer = e.getManagerId() != null && !e.getManagerId().equals(e.getId()) && staff.containsKey(e.getManagerId()) ? e.getManagerId() : null;
        return HrPerfReview.of(tenantId.getValue(), c.getId(), e.getId(), reviewer, e.getJobTitle());
    }

    private CycleRow row(HrPerfCycle c, List<HrPerfReview> rs) {
        int self = 0, mgr = 0, sub = 0, rel = 0, ex = 0;
        for (HrPerfReview r : rs) {
            switch (r.getStatus()) {
                case PerfRules.AWAITING_SELF -> self++;
                case PerfRules.AWAITING_MANAGER -> mgr++;
                case PerfRules.SUBMITTED -> sub++;
                case PerfRules.SHARED, PerfRules.ACKNOWLEDGED -> rel++;
                case PerfRules.EXCLUDED -> ex++;
                default -> { }
            }
        }
        return new CycleRow(c.getId(), c.getName(), c.getPeriodStart(), c.getPeriodEnd(), c.getSelfDue(), c.getManagerDue(), c.isInclude360(), c.getStatus(), rs.size(), self, mgr, sub, rel, ex);
    }

    private CycleDetail detail(TenantId tenantId, HrPerfCycle c, Set<String> held, UUID userId) {
        List<HrPerfReview> rs = reviewRepo.forCycle(tenantId, c.getId());
        UUID myId = support.me(tenantId, userId).map(HrEmployee::getId).orElse(null);
        List<ReviewRow> rows = views.rows(tenantId, rs, r -> myId == null || !myId.equals(r.getEmployeeId()) || PerfRules.SHARED.equals(r.getStatus()) || PerfRules.ACKNOWLEDGED.equals(r.getStatus()));
        int without = (int) rs.stream().filter(r -> r.getReviewerId() == null && !PerfRules.EXCLUDED.equals(r.getStatus())).count();
        List<String> actions = new ArrayList<>();
        if (PerfRules.canManage(held)) {
            if (PerfRules.DRAFT.equals(c.getStatus())) actions.addAll(List.of(A_EDIT, A_LAUNCH, A_DELETE));
            if (PerfRules.OPEN.equals(c.getStatus())) actions.addAll(List.of(A_ADD, A_CLOSE));
        }
        return new CycleDetail(row(c, rs), rows, without, support.events(tenantId, HrPerfEvent.CYCLE, c.getId()), actions);
    }
}
