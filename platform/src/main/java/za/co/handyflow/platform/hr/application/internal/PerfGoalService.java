package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * Goals. An employee proposes a goal and their manager approves it, or the manager sets it. Progress is noted as it happens, the manager
 * closes the goal with how it ended, and every step is kept in the goal's history.
 */
@Service
@RequiredArgsConstructor
public class PerfGoalService {

    private static final int LIST_LIMIT = 1000;

    private final HrPerfGoalRepository goalRepo;
    private final HrPerfCycleRepository cycleRepo;
    private final HrEmployeeRepository employeeRepo;
    private final PerfSupport support;
    private final PerfNotifier notifier;

    // ── Reading ───────────────────────────────────────────────────────────────────────────────────

    /** Goals for "mine", "team" (the people who report to me) or "all" (needs HR_PERF_READ), or for one employee the person may see. */
    @Transactional(readOnly = true)
    public List<GoalView> list(TenantId tenantId, String scope, UUID employeeId, Set<String> held, UUID userId) {
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        List<HrPerfGoal> goals = new ArrayList<>();
        if (employeeId != null) {
            HrEmployee subject = support.employee(tenantId, employeeId);
            if (!who(me, subject, held, true).any()) throw new ResourceNotFoundException("Employee", employeeId.toString());
            goals.addAll(goalRepo.forEmployee(tenantId, employeeId));
        } else if ("team".equals(scope)) {
            HrEmployee boss = support.requireMe(tenantId, userId);
            for (HrEmployee e : employeeRepo.findNotTerminated(tenantId)) if (boss.getId().equals(e.getManagerId())) goals.addAll(goalRepo.forEmployee(tenantId, e.getId()));
        } else if ("all".equals(scope)) {
            PerfSupport.requireRead(held);
            goals.addAll(goalRepo.forTenant(tenantId));
        } else {
            goals.addAll(goalRepo.forEmployee(tenantId, support.requireMe(tenantId, userId).getId()));
        }
        if (goals.size() > LIST_LIMIT) goals = goals.subList(0, LIST_LIMIT);
        return views(tenantId, goals, me, held, false);
    }

    @Transactional(readOnly = true)
    public GoalView get(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        Found f = find(tenantId, id, held, userId);
        return views(tenantId, List.of(f.goal()), f.me(), held, true).get(0);
    }

    /** The goals that belong in a review: those set for the cycle, and those without a cycle that fall due within its period. */
    List<GoalView> forReview(TenantId tenantId, HrPerfCycle cycle, HrEmployee subject, HrEmployee me, Set<String> held) {
        List<HrPerfGoal> goals = goalRepo.forEmployee(tenantId, subject.getId()).stream()
                .filter(g -> !PerfRules.CANCELLED.equals(g.getStatus()))
                .filter(g -> cycle.getId().equals(g.getCycleId())
                        || (g.getCycleId() == null && !g.getDueDate().isBefore(cycle.getPeriodStart()) && !g.getDueDate().isAfter(cycle.getPeriodEnd())))
                .toList();
        return views(tenantId, goals, me, held, false);
    }

    // ── Changing ──────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public GoalView create(TenantId tenantId, GoalRequest req, UUID userId, Set<String> held) {
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        UUID target = req.employeeId() != null ? req.employeeId() : (me == null ? null : me.getId());
        HrEmployee subject = support.serving(tenantId, target);
        PerfRules.Who w = who(me, subject, held, false);
        if (!w.subject() && !w.reviewer() && !w.hr()) throw new AccessDeniedException("You can set goals for yourself and for the people who report to you.");
        PerfSupport.fail(PerfRules.goalProblem(req.title(), req.description(), req.measure(), req.dueDate(), support.today(), true));
        if (req.cycleId() != null) {
            HrPerfCycle c = cycleRepo.findByTenantAndId(tenantId, req.cycleId()).orElseThrow(() -> new ResourceNotFoundException("Cycle", String.valueOf(req.cycleId())));
            if (PerfRules.CLOSED.equals(c.getStatus())) throw new IllegalStateException("That review cycle is closed.");
        }
        HrPerfGoal g = HrPerfGoal.of(tenantId.getValue(), subject.getId(), req.cycleId(), req.title(), req.description(), req.measure(), req.dueDate(),
                PerfRules.initialGoalStatus(w.subject()), userId);
        goalRepo.save(g);
        support.event(tenantId, HrPerfEvent.GOAL, g.getId(), w.subject() ? "PROPOSED" : "SET", null, userId);
        if (w.subject()) notifier.goalProposed(tenantId, g, subject);
        else notifier.goalSet(tenantId, g, subject);
        return views(tenantId, List.of(g), me, held, true).get(0);
    }

    @Transactional
    public GoalView edit(TenantId tenantId, UUID id, GoalEditRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.G_EDIT, "You cannot change this goal.", "A goal that is " + words(f.goal().getStatus()) + " cannot be changed.");
        PerfSupport.fail(PerfRules.goalProblem(req.title(), req.description(), req.measure(), req.dueDate(), support.today(), false));
        HrPerfGoal g = f.goal();
        g.edit(req.title(), req.description(), req.measure(), req.dueDate());
        goalRepo.save(g);
        support.event(tenantId, HrPerfEvent.GOAL, g.getId(), "EDITED", null, userId);
        return views(tenantId, List.of(g), f.me(), held, true).get(0);
    }

    @Transactional
    public GoalView approve(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.G_APPROVE, "Only the employee's manager can approve a goal.", "Only a proposed goal can be approved.");
        HrPerfGoal g = f.goal();
        g.approve(userId);
        goalRepo.save(g);
        support.event(tenantId, HrPerfEvent.GOAL, g.getId(), "APPROVED", null, userId);
        notifier.goalApproved(tenantId, g, f.subject());
        return views(tenantId, List.of(g), f.me(), held, true).get(0);
    }

    @Transactional
    public GoalView progress(TenantId tenantId, UUID id, ProgressRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.G_PROGRESS, "You cannot record progress on this goal.", "Progress can only be recorded on a goal that is under way.");
        PerfSupport.fail(PerfRules.progressProblem(req.progress(), req.note()));
        HrPerfGoal g = f.goal();
        g.progress(req.progress());
        goalRepo.save(g);
        String note = req.progress() + "%" + (req.note() == null || req.note().isBlank() ? "" : " – " + req.note().trim());
        support.event(tenantId, HrPerfEvent.GOAL, g.getId(), "PROGRESS", note, userId);
        return views(tenantId, List.of(g), f.me(), held, true).get(0);
    }

    @Transactional
    public GoalView close(TenantId tenantId, UUID id, GoalCloseRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.G_CLOSE, "Only the employee's manager can close a goal.", "Only a goal that is under way can be closed.");
        PerfSupport.fail(PerfRules.goalCloseProblem(req.outcome(), req.note()));
        HrPerfGoal g = f.goal();
        g.close(req.outcome(), req.note(), userId);
        goalRepo.save(g);
        support.event(tenantId, HrPerfEvent.GOAL, g.getId(), "CLOSED", words(req.outcome()) + (req.note() == null || req.note().isBlank() ? "" : " – " + req.note().trim()), userId);
        return views(tenantId, List.of(g), f.me(), held, true).get(0);
    }

    @Transactional
    public GoalView cancel(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.G_CANCEL, "You cannot cancel this goal.", "A goal that is " + words(f.goal().getStatus()) + " cannot be cancelled.");
        PerfSupport.fail(PerfRules.goalCancelProblem(req.reason()));
        HrPerfGoal g = f.goal();
        g.cancel(req.reason(), userId);
        goalRepo.save(g);
        support.event(tenantId, HrPerfEvent.GOAL, g.getId(), "CANCELLED", req.reason().trim(), userId);
        return views(tenantId, List.of(g), f.me(), held, true).get(0);
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────

    private record Found(HrPerfGoal goal, HrEmployee subject, HrEmployee me, PerfRules.Who who) {}

    private Found find(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrPerfGoal g = goalRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Goal", String.valueOf(id)));
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        HrEmployee subject = support.employee(tenantId, g.getEmployeeId());
        if (!who(me, subject, held, true).any()) throw new ResourceNotFoundException("Goal", String.valueOf(id));
        return new Found(g, subject, me, who(me, subject, held, false));
    }

    /** For goals, the reviewer slot is the employee's manager. Reading counts HR_PERF_READ as HR; changing needs HR_PERF_MANAGE. */
    private PerfRules.Who who(HrEmployee me, HrEmployee subject, Set<String> held, boolean forReading) {
        boolean self = me != null && me.getId().equals(subject.getId());
        return PerfRules.Who.of(self, support.isManagerOf(me, subject), forReading ? PerfRules.canRead(held) : PerfRules.canManage(held));
    }

    private void require(Found f, String action, String denied, String state) {
        PerfRules.Who w = f.who();
        List<String> actions = PerfRules.goalActions(w, f.goal().getStatus());
        // An employee may record progress on their goal and change or cancel a proposal; changing an approved goal is their manager's call.
        String status = f.goal().getStatus();
        boolean employeeMay = PerfRules.G_PROGRESS.equals(action)
                || (!PerfRules.ACTIVE.equals(status) && (PerfRules.G_EDIT.equals(action) || PerfRules.G_CANCEL.equals(action)));
        boolean roleOk = w.reviewer() || w.hr() || (w.subject() && employeeMay);
        PerfSupport.require(actions, action, roleOk, denied, state);
    }

    private List<GoalView> views(TenantId tenantId, List<HrPerfGoal> goals, HrEmployee me, Set<String> held, boolean withHistory) {
        Set<UUID> ids = new HashSet<>();
        Set<UUID> cycleIds = new HashSet<>();
        for (HrPerfGoal g : goals) { ids.add(g.getEmployeeId()); if (g.getCycleId() != null) cycleIds.add(g.getCycleId()); }
        Map<UUID, HrEmployee> people = new HashMap<>();
        for (HrEmployee e : employeeRepo.findAllById(ids)) people.put(e.getId(), e);
        Map<UUID, String> cycles = new HashMap<>();
        if (!cycleIds.isEmpty()) for (HrPerfCycle c : cycleRepo.findAllById(cycleIds)) cycles.put(c.getId(), c.getName());
        Map<UUID, String> users = new HashMap<>();
        LocalDate today = support.today();
        List<GoalView> out = new ArrayList<>();
        for (HrPerfGoal g : goals) {
            HrEmployee subject = people.get(g.getEmployeeId());
            if (subject == null) continue;
            PerfRules.Who w = who(me, subject, held, false);
            boolean open = PerfRules.ACTIVE.equals(g.getStatus()) || PerfRules.PROPOSED.equals(g.getStatus());
            out.add(new GoalView(g.getId(), g.getEmployeeId(), subject.getFullName(), g.getCycleId(), cycles.get(g.getCycleId()), g.getTitle(), g.getDescription(),
                    g.getMeasure(), g.getDueDate(), g.getStatus(), g.getProgress(), open && g.getDueDate().isBefore(today), g.getOutcomeNote(),
                    support.userName(tenantId, g.getSetBy(), users),
                    withHistory ? support.events(tenantId, HrPerfEvent.GOAL, g.getId()) : List.of(), PerfRules.goalActions(w, g.getStatus())));
        }
        return out;
    }

    private static String words(String code) { return code == null ? "" : code.toLowerCase().replace('_', ' '); }
}
