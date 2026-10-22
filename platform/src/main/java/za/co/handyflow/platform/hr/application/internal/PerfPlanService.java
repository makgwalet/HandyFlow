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
 * Improvement plans. A plan says what the shortfall is, the standard required, the support the company will give and the time allowed. HR
 * tells the employee (which records the date), the manager checks in as it runs, and HR closes it met or not met. A plan can belong to a
 * poor performance case; it then shows on the case, and the case's own confidentiality applies to it.
 */
@Service
@RequiredArgsConstructor
public class PerfPlanService {

    private static final int LIST_LIMIT = 300;

    private final HrPerfPlanRepository planRepo;
    private final HrPerfCheckinRepository checkinRepo;
    private final HrErCaseRepository caseRepo;
    private final ErCaseSupport caseSupport;
    private final ErPolicyService policyService;
    private final PerfSupport support;

    // ── Reading ───────────────────────────────────────────────────────────────────────────────────

    /** Plans as "mine", "team" (I own them), "all" (HR_PERF_READ), for one employee (HR_PERF_READ), or for a case the person may see. */
    @Transactional(readOnly = true)
    public List<PlanRow> list(TenantId tenantId, String scope, UUID employeeId, UUID caseId, Set<String> held, UUID userId) {
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        List<HrPerfPlan> plans;
        if (caseId != null) {
            caseSupport.find(tenantId, caseId, held, userId);
            plans = planRepo.forCase(tenantId, caseId);
        } else if (employeeId != null) {
            if (me == null || !me.getId().equals(employeeId)) PerfSupport.requireRead(held);
            plans = planRepo.forEmployee(tenantId, employeeId);
        } else if ("all".equals(scope)) {
            PerfSupport.requireRead(held);
            plans = planRepo.forTenant(tenantId);
        } else if ("team".equals(scope)) {
            UUID mine = support.requireMe(tenantId, userId).getId();
            plans = planRepo.forTenant(tenantId).stream().filter(p -> mine.equals(p.getOwnerId())).toList();
        } else {
            plans = planRepo.forEmployee(tenantId, support.requireMe(tenantId, userId).getId());
        }
        ErPolicy policy = policyService.get(tenantId);
        List<HrPerfPlan> shown = new ArrayList<>();
        Map<UUID, HrErCase> cases = new HashMap<>();
        for (HrPerfPlan p : plans) {
            if (shown.size() >= LIST_LIMIT) break;
            if (visible(tenantId, p, me, held, userId, cases)) shown.add(p);
        }
        return rows(tenantId, shown, policy, cases);
    }

    @Transactional(readOnly = true)
    public PlanDetail get(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        return detail(tenantId, find(tenantId, id, held, userId), held);
    }

    // ── Changing ──────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public PlanDetail create(TenantId tenantId, PlanRequest req, UUID userId, Set<String> held) {
        PerfSupport.requireManage(held);
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        HrEmployee subject = support.serving(tenantId, req.employeeId());
        if (me != null && me.getId().equals(subject.getId())) throw new AccessDeniedException("You cannot make an improvement plan about yourself.");
        UUID owner = owner(tenantId, req.ownerId() != null ? req.ownerId() : subject.getManagerId(), subject);
        if (req.caseId() != null) linkable(tenantId, req.caseId(), subject, held, userId);
        PerfSupport.fail(PerfRules.planProblem(req.title(), req.shortfall(), req.standard(), req.support(), req.startDate(), req.endDate()));
        HrPerfPlan p = HrPerfPlan.draft(tenantId.getValue(), subject.getId(), owner, req.caseId(), null, req.title(), req.shortfall(), req.standard(), req.support(),
                req.startDate(), req.endDate(), userId);
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "CREATED", null, userId);
        return detail(tenantId, find(tenantId, p.getId(), held, userId), held);
    }

    @Transactional
    public PlanDetail update(TenantId tenantId, UUID id, PlanRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_EDIT, "Only HR can change an improvement plan.", "A plan can be rewritten only until the employee has been told about it.");
        HrPerfPlan p = f.plan();
        UUID owner = owner(tenantId, req.ownerId() != null ? req.ownerId() : p.getOwnerId(), f.subject());
        if (req.caseId() != null && !req.caseId().equals(p.getCaseId())) linkable(tenantId, req.caseId(), f.subject(), held, userId);
        PerfSupport.fail(PerfRules.planProblem(req.title(), req.shortfall(), req.standard(), req.support(), req.startDate(), req.endDate()));
        p.edit(owner, req.caseId(), p.getReviewId(), req.title(), req.shortfall(), req.standard(), req.support(), req.startDate(), req.endDate());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "EDITED", null, userId);
        return detail(tenantId, f, held);
    }

    /** Tells the employee: from here the plan is theirs to read, and its shortfall, standard and support are fixed. */
    @Transactional
    public PlanDetail start(TenantId tenantId, UUID id, StartPlanRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_START, "Only HR can start an improvement plan.", "Only a draft plan can be started.");
        PerfSupport.fail(PerfRules.startProblem(req.communicatedOn(), support.today()));
        openCase(tenantId, f.plan());
        HrPerfPlan p = f.plan();
        p.start(req.communicatedOn());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "STARTED", "Employee told on " + req.communicatedOn(), userId);
        return detail(tenantId, f, held);
    }

    @Transactional
    public PlanDetail checkin(TenantId tenantId, UUID id, CheckinRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_CHECKIN, "Only the plan's owner or HR can record a check-in.", "Check-ins are recorded while the plan is running.");
        HrPerfPlan p = f.plan();
        PerfSupport.fail(PerfRules.checkinProblem(req.onDate(), p.getStartDate(), support.today(), req.progress(), req.note()));
        HrPerfCheckin c = HrPerfCheckin.of(tenantId.getValue(), p.getId(), req.onDate(), req.progress(), req.note(), userId);
        checkinRepo.save(c);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "CHECKIN", req.progress().toLowerCase().replace('_', ' '), userId);
        return detail(tenantId, f, held);
    }

    @Transactional
    public PlanDetail extend(TenantId tenantId, UUID id, ExtendRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_EXTEND, "Only HR can give a plan more time.", "Only a running plan can be extended.");
        HrPerfPlan p = f.plan();
        PerfSupport.fail(PerfRules.extendProblem(req.endDate(), p.getEndDate(), req.reason()));
        p.extend(req.endDate());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "EXTENDED", "To " + req.endDate() + " – " + req.reason().trim(), userId);
        return detail(tenantId, f, held);
    }

    @Transactional
    public PlanDetail close(TenantId tenantId, UUID id, ClosePlanRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_CLOSE, "Only HR can end an improvement plan.", "Only a running plan can be ended.");
        HrPerfPlan p = f.plan();
        PerfSupport.fail(PerfRules.closePlanProblem(req.outcome(), req.note(), support.today(), p.getEndDate(), checkinRepo.forPlan(p.getId()).size()));
        p.close(req.outcome(), req.note(), support.today());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), PerfRules.MET.equals(req.outcome()) ? "MET" : "NOT_MET", req.note().trim(), userId);
        return detail(tenantId, f, held);
    }

    @Transactional
    public PlanDetail cancel(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_CANCEL, "Only HR can cancel an improvement plan.", "A plan that has ended cannot be cancelled.");
        PerfSupport.fail(PerfRules.cancelPlanProblem(req.reason()));
        HrPerfPlan p = f.plan();
        p.cancel(req.reason(), support.today());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "CANCELLED", req.reason().trim(), userId);
        return detail(tenantId, f, held);
    }

    @Transactional
    public PlanDetail acknowledge(TenantId tenantId, UUID id, AcknowledgeRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_ACKNOWLEDGE, "Only the employee can acknowledge their plan.", "This plan has already been acknowledged, or has not been started.");
        PerfSupport.fail(PerfRules.acknowledgeProblem(req == null ? null : req.comment()));
        HrPerfPlan p = f.plan();
        p.acknowledge(userId, req == null ? null : req.comment());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "ACKNOWLEDGED", null, userId);
        return detail(tenantId, f, held);
    }

    /** Attaches the plan to a poor performance case about the same employee. */
    @Transactional
    public PlanDetail linkCase(TenantId tenantId, UUID id, CaseLinkRequest req, UUID userId, Set<String> held) {
        Found f = find(tenantId, id, held, userId);
        require(f, PerfRules.P_LINK, "Only HR can link a plan to a case.", "A plan that has ended cannot be linked to a case.");
        HrErCase c = linkable(tenantId, req.caseId(), f.subject(), held, userId);
        HrPerfPlan p = f.plan();
        p.linkCase(c.getId());
        planRepo.save(p);
        support.event(tenantId, HrPerfEvent.PLAN, p.getId(), "CASE_LINKED", c.getCaseNumber(), userId);
        return detail(tenantId, find(tenantId, id, held, userId), held);
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────

    private record Found(HrPerfPlan plan, HrEmployee subject, HrEmployee me, PerfRules.Who actionWho, PerfRules.Who sightWho) {}

    private Found find(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrPerfPlan p = planRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Improvement plan", String.valueOf(id)));
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        if (!visible(tenantId, p, me, held, userId, new HashMap<>())) throw new ResourceNotFoundException("Improvement plan", String.valueOf(id));
        HrEmployee subject = support.employee(tenantId, p.getEmployeeId());
        return new Found(p, subject, me, who(me, p, PerfRules.canManage(held)), who(me, p, PerfRules.canRead(held)));
    }

    private PerfRules.Who who(HrEmployee me, HrPerfPlan p, boolean hr) {
        boolean self = me != null && me.getId().equals(p.getEmployeeId());
        boolean owner = me != null && p.getOwnerId() != null && me.getId().equals(p.getOwnerId());
        return PerfRules.Who.of(self, owner, hr);
    }

    /** The employee sees a plan once they have been told. HR and the owner see it too, but a confidential case's plan stays with the case's people. */
    private boolean visible(TenantId tenantId, HrPerfPlan p, HrEmployee me, Set<String> held, UUID userId, Map<UUID, HrErCase> cases) {
        PerfRules.Who w = who(me, p, PerfRules.canRead(held));
        if (w.subject()) return PerfRules.canSeePlan(w, false, false, p.getCommunicatedOn() != null);
        HrErCase c = null;
        if (p.getCaseId() != null) c = cases.computeIfAbsent(p.getCaseId(), k -> caseRepo.findByTenantAndId(tenantId, k).orElse(null));
        boolean caseOk = c != null && ErRules.canSee(held, userId, c.isConfidential(), c.getOwnerUserId());
        boolean hrOk = w.hr() && (c == null || !c.isConfidential() || caseOk);
        return hrOk || w.reviewer() || caseOk;
    }

    private void require(Found f, String action, String denied, String state) {
        List<String> actions = PerfRules.planActions(f.actionWho(), f.plan().getStatus(), f.plan().getAckAt() != null);
        boolean roleOk = f.actionWho().hr() || f.actionWho().subject() && PerfRules.P_ACKNOWLEDGE.equals(action) || f.actionWho().reviewer() && PerfRules.P_CHECKIN.equals(action);
        PerfSupport.require(actions, action, roleOk, denied, state);
    }

    private UUID owner(TenantId tenantId, UUID ownerId, HrEmployee subject) {
        if (ownerId == null) return null;
        if (ownerId.equals(subject.getId())) throw new IllegalArgumentException("The owner of the plan cannot be the employee it is about.");
        return support.serving(tenantId, ownerId).getId();
    }

    /** A case the plan may be linked to: one the person can work on, which is a poor performance case about this employee. */
    private HrErCase linkable(TenantId tenantId, UUID caseId, HrEmployee subject, Set<String> held, UUID userId) {
        HrErCase c = caseSupport.findToAct(tenantId, caseId, held, userId);
        PerfSupport.fail(PerfRules.caseLinkProblem(c.getCaseType(), c.getCategory(), c.getStatus(), c.getEmployeeId(), subject.getId()));
        return c;
    }

    private void openCase(TenantId tenantId, HrPerfPlan p) {
        if (p.getCaseId() == null) return;
        caseRepo.findByTenantAndId(tenantId, p.getCaseId()).ifPresent(c -> {
            if (ErRules.CLOSED.equals(c.getStatus()) || ErRules.WITHDRAWN.equals(c.getStatus())) throw new IllegalStateException("The case this plan belongs to is closed.");
        });
    }

    private List<PlanRow> rows(TenantId tenantId, List<HrPerfPlan> plans, ErPolicy policy, Map<UUID, HrErCase> cases) {
        List<UUID> people = new ArrayList<>();
        for (HrPerfPlan p : plans) { people.add(p.getEmployeeId()); people.add(p.getOwnerId()); }
        Map<UUID, String> names = support.names(people);
        List<PlanRow> out = new ArrayList<>();
        for (HrPerfPlan p : plans) {
            HrErCase c = p.getCaseId() == null ? null : cases.computeIfAbsent(p.getCaseId(), k -> caseRepo.findByTenantAndId(tenantId, k).orElse(null));
            List<HrPerfCheckin> checkins = checkinRepo.forPlan(p.getId());
            String last = checkins.isEmpty() ? null : checkins.get(checkins.size() - 1).getProgress();
            out.add(new PlanRow(p.getId(), p.getEmployeeId(), names.getOrDefault(p.getEmployeeId(), "Unknown"), p.getOwnerId(),
                    p.getOwnerId() == null ? null : names.getOrDefault(p.getOwnerId(), "Unknown"), p.getCaseId(), c == null ? null : c.getCaseNumber(), p.getTitle(), p.getStatus(),
                    p.getStartDate(), p.getEndDate(), p.getCommunicatedOn(), PerfRules.shorterThanMinimum(p.getStartDate(), p.getEndDate(), policy.minReviewDays()), last, p.getAckAt() != null));
        }
        out.sort(Comparator.comparing(PlanRow::startDate).reversed());
        return out;
    }

    private PlanDetail detail(TenantId tenantId, Found f, Set<String> held) {
        HrPerfPlan p = f.plan();
        ErPolicy policy = policyService.get(tenantId);
        Map<UUID, HrErCase> cases = new HashMap<>();
        PlanRow row = rows(tenantId, List.of(p), policy, cases).get(0);
        Map<UUID, String> users = new HashMap<>();
        List<CheckinView> checkins = checkinRepo.forPlan(p.getId()).stream()
                .map(c -> new CheckinView(c.getId(), c.getOnDate(), c.getProgress(), c.getNote(), support.userName(tenantId, c.getDoneBy(), users), c.getDoneAt())).toList();
        boolean subject = f.actionWho().subject();
        return new PlanDetail(row, p.getShortfall(), p.getStandard(), p.getSupport(), p.getClosedOn(), p.getOutcomeNote(), p.getAckAt(), p.getEmployeeComment(), policy.minReviewDays(),
                checkins, subject ? List.of() : support.events(tenantId, HrPerfEvent.PLAN, p.getId()),
                PerfRules.planActions(f.actionWho(), p.getStatus(), p.getAckAt() != null));
    }
}
