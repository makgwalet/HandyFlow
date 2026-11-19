package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.BcPlanRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.BcProcessRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.BcTestRepository;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.ContinuityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business continuity: critical processes with recovery objectives, continuity plans and exercises.
 * Approving a plan retires the process's previous approved plan in the same step, so there is only ever one plan in force and its text is never altered in place.
 * The approver must differ from the author. Exercises are append-only and can only be recorded against an approved plan.
 * Overdue exercises, plan reviews and impact-analysis reviews are derived from dates (see ContinuityRules), never stored.
 */
@Service
@RequiredArgsConstructor
public class ContinuityService {

    static final String PROCESS = "BC_PROCESS", PLAN = "BC_PLAN";

    private final BcProcessRepository processes;
    private final BcPlanRepository plans;
    private final BcTestRepository tests;
    private final RegulatoryAuditService trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    // ------------------------------------------------------------------ processes

    @Transactional
    public ProcessDetail createProcess(TenantId t, ProcessRequest r, UUID uid, String uname) {
        BcProcess p = guarded(() -> BcProcess.create(t, String.format("BCP-%05d", processes.nextRef()), r.name(), r.description(), r.ownerName(), r.department(), r.criticality(),
                r.rtoHours(), r.rpoHours(), r.dependencies(), r.biaReviewMonths() == 0 ? 12 : r.biaReviewMonths()));
        processes.save(p);
        trail.record(t.getValue(), PROCESS, p.getId(), "CREATED", p.getReference() + " " + p.getName() + ", RTO " + p.getRtoHours() + "h, RPO " + p.getRpoHours() + "h", uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ProcessDetail updateProcess(TenantId t, UUID id, ProcessRequest r, UUID uid, String uname) {
        BcProcess p = loadProcess(t, id);
        int oldRto = p.getRtoHours(), oldRpo = p.getRpoHours();
        guarded(() -> { p.update(r.name(), r.description(), r.ownerName(), r.department(), r.criticality(), r.rtoHours(), r.rpoHours(), r.dependencies(), r.biaReviewMonths() == 0 ? p.getBiaReviewMonths() : r.biaReviewMonths()); return null; });
        processes.save(p);
        String objectives = oldRto != p.getRtoHours() || oldRpo != p.getRpoHours() ? "; RTO " + oldRto + "h→" + p.getRtoHours() + "h, RPO " + oldRpo + "h→" + p.getRpoHours() + "h" : "";
        trail.record(t.getValue(), PROCESS, id, "UPDATED", "Details changed" + objectives, uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ProcessDetail biaReview(TenantId t, UUID id, BiaReviewRequest r, UUID uid, String uname) {
        BcProcess p = loadProcess(t, id);
        guarded(() -> { p.markBiaReviewed(r.reviewedOn(), today()); return null; });
        processes.save(p);
        trail.record(t.getValue(), PROCESS, id, "BIA_REVIEWED", "Impact analysis reviewed on " + r.reviewedOn(), uid, uname);
        return detail(t, p);
    }

    /** Retiring a process retires its approved plan too (a plan with nothing to protect should not look current). Drafts are retired as well. */
    @Transactional
    public ProcessDetail retireProcess(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        BcProcess p = loadProcess(t, id);
        guarded(() -> { p.retire(r.reason()); return null; });
        processes.save(p);
        for (BcPlan plan : plans.findForProcess(t, id)) {
            if (plan.getStatus() == BcPlanStatus.RETIRED) continue;
            plan.retire("Process retired: " + r.reason().trim());
            plans.save(plan);
            trail.record(t.getValue(), PLAN, plan.getId(), "RETIRED", "Process retired", uid, uname);
        }
        trail.record(t.getValue(), PROCESS, id, "RETIRED", r.reason().trim(), uid, uname);
        return detail(t, p);
    }

    @Transactional(readOnly = true)
    public List<ProcessResponse> listProcesses(TenantId t, boolean includeRetired) {
        Snapshot s = snapshot(t);
        return processes.search(t, includeRetired).stream().map(p -> response(p, s)).toList();
    }

    @Transactional(readOnly = true)
    public ProcessDetail getProcess(TenantId t, UUID id) { return detail(t, loadProcess(t, id)); }

    // ------------------------------------------------------------------ plans

    @Transactional
    public ProcessDetail createPlan(TenantId t, UUID processId, PlanRequest r, UUID uid, String uname) {
        BcProcess p = loadProcess(t, processId);
        if (p.getStatus() == BcProcessStatus.RETIRED) throw new HandyFlowException("A retired process cannot have new plans", HttpStatus.CONFLICT, "INVALID_STATE");
        BcPlan plan = guarded(() -> BcPlan.draft(t, String.format("BCPL-%05d", processes.nextRef()), processId, r.title(), r.versionLabel(), r.ownerName(), r.content(),
                r.reviewMonths() == 0 ? 12 : r.reviewMonths(), uid, uname));
        plans.save(plan);
        trail.record(t.getValue(), PLAN, plan.getId(), "DRAFTED", plan.getReference() + " " + plan.getVersionLabel() + " for " + p.getReference(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ProcessDetail updatePlan(TenantId t, UUID planId, PlanRequest r, UUID uid, String uname) {
        BcPlan plan = loadPlan(t, planId);
        guarded(() -> { plan.update(r.title(), r.versionLabel(), r.ownerName(), r.content(), r.reviewMonths() == 0 ? plan.getReviewMonths() : r.reviewMonths()); return null; });
        plans.save(plan);
        trail.record(t.getValue(), PLAN, planId, "DRAFT_UPDATED", plan.getVersionLabel(), uid, uname);
        return detail(t, loadProcess(t, plan.getProcessId()));
    }

    @Transactional
    public ProcessDetail approvePlan(TenantId t, UUID planId, UUID uid, String uname) {
        BcPlan plan = loadPlan(t, planId);
        BcProcess p = loadProcess(t, plan.getProcessId());
        if (p.getStatus() == BcProcessStatus.RETIRED) throw new HandyFlowException("A retired process cannot have an approved plan", HttpStatus.CONFLICT, "INVALID_STATE");
        if (plan.getStatus() != BcPlanStatus.DRAFT) throw new HandyFlowException("Only a draft can be approved", HttpStatus.CONFLICT, "INVALID_STATE");
        // Checked before the old plan is touched, so a refused approval leaves the current plan in force.
        guarded(() -> { if (uid != null && uid.equals(plan.getAuthorUserId())) throw new IllegalStateException("A plan cannot be approved by its author"); return null; });
        plans.findApprovedForProcess(t, p.getId()).filter(old -> !old.getId().equals(planId)).ifPresent(old -> {
            old.retire("Superseded by " + plan.getReference() + " " + plan.getVersionLabel());
            plans.saveAndFlush(old);
            trail.record(t.getValue(), PLAN, old.getId(), "RETIRED", "Superseded by " + plan.getReference(), uid, uname);
        });
        guarded(() -> { plan.approve(uid, uname, today()); return null; });
        plans.save(plan);
        trail.record(t.getValue(), PLAN, planId, "APPROVED", plan.getVersionLabel() + ", next review " + plan.getNextReviewDue(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ProcessDetail confirmPlanReview(TenantId t, UUID planId, UUID uid, String uname) {
        BcPlan plan = loadPlan(t, planId);
        guarded(() -> { plan.confirmReview(today()); return null; });
        plans.save(plan);
        trail.record(t.getValue(), PLAN, planId, "REVIEW_CONFIRMED", "Next review " + plan.getNextReviewDue(), uid, uname);
        return detail(t, loadProcess(t, plan.getProcessId()));
    }

    @Transactional
    public ProcessDetail retirePlan(TenantId t, UUID planId, ReasonRequest r, UUID uid, String uname) {
        BcPlan plan = loadPlan(t, planId);
        guarded(() -> { plan.retire(r.reason()); return null; });
        plans.save(plan);
        trail.record(t.getValue(), PLAN, planId, "RETIRED", r.reason().trim(), uid, uname);
        return detail(t, loadProcess(t, plan.getProcessId()));
    }

    // ------------------------------------------------------------------ exercises

    @Transactional
    public ProcessDetail recordTest(TenantId t, UUID planId, TestRequest r, UUID uid, String uname) {
        BcPlan plan = loadPlan(t, planId);
        BcProcess p = loadProcess(t, plan.getProcessId());
        if (plan.getStatus() != BcPlanStatus.APPROVED) throw new HandyFlowException("Only an approved plan can be exercised", HttpStatus.CONFLICT, "INVALID_STATE");
        BcTest test = guarded(() -> BcTest.record(t, planId, p.getRtoHours(), r.testDate(), r.testType(), r.outcome(), r.achievedRtoHours(), r.issues(), r.actions(), r.conductedBy(), uid, uname, today()));
        tests.save(test);
        trail.record(t.getValue(), PLAN, planId, "TESTED", r.testType().name() + " on " + r.testDate() + ": " + r.outcome().name()
                + (test.getAchievedRtoHours() == null ? "" : ", recovered in " + test.getAchievedRtoHours() + "h against " + p.getRtoHours() + "h"), uid, uname);
        return detail(t, p);
    }

    // ------------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public ContinuitySummary summary(TenantId t) {
        Snapshot s = snapshot(t);
        List<ProcessResponse> active = processes.search(t, false).stream().map(p -> response(p, s)).toList();
        return new ContinuitySummary(active.size(),
                active.stream().filter(p -> p.criticality() == Criticality.CRITICAL).count(),
                active.stream().filter(p -> p.approvedPlanId() == null).count(),
                active.stream().filter(p -> p.approvedPlanId() == null && p.criticality() == Criticality.CRITICAL).count(),
                active.stream().filter(ProcessResponse::biaOverdue).count(),
                active.stream().filter(ProcessResponse::planReviewOverdue).count(),
                active.stream().filter(ProcessResponse::testOverdue).count(),
                active.stream().filter(p -> p.lastTestOutcome() == BcTestOutcome.FAILED || Boolean.FALSE.equals(p.lastTestRtoMet())).count());
    }

    // ------------------------------------------------------------------ internals

    /** Everything needed to derive process states, loaded once per call. */
    private record Snapshot(Map<UUID, BcPlan> approvedByProcess, Map<UUID, BcTest> lastTestByProcess) {}

    private Snapshot snapshot(TenantId t) {
        List<BcPlan> all = plans.findAllForTenant(t);
        Map<UUID, BcPlan> approved = all.stream().filter(p -> p.getStatus() == BcPlanStatus.APPROVED).collect(Collectors.toMap(BcPlan::getProcessId, p -> p, (a, b) -> a));
        Map<UUID, UUID> planToProcess = all.stream().collect(Collectors.toMap(BcPlan::getId, BcPlan::getProcessId));
        Map<UUID, BcTest> last = new HashMap<>();
        // Newest first, so the first test seen per process is its latest. Tests of earlier (retired) plans still count: the process was exercised.
        for (BcTest x : tests.findAllForTenant(t)) {
            UUID process = planToProcess.get(x.getPlanId());
            if (process != null) last.putIfAbsent(process, x);
        }
        return new Snapshot(approved, last);
    }

    private ProcessResponse response(BcProcess p, Snapshot s) {
        LocalDate today = today();
        BcPlan plan = s.approvedByProcess().get(p.getId());
        BcTest last = s.lastTestByProcess().get(p.getId());
        LocalDate lastDate = last == null ? null : last.getTestDate();
        LocalDate approvedOn = plan == null ? null : plan.getApprovedOn();
        boolean active = p.getStatus() == BcProcessStatus.ACTIVE;
        LocalDate recordedOn = p.getCreatedAt().atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate();
        return new ProcessResponse(p.getId(), p.getReference(), p.getName(), p.getDescription(), p.getOwnerName(), p.getDepartment(), p.getCriticality(), p.getRtoHours(), p.getRpoHours(),
                p.getDependencies(), p.getStatus(), p.getBiaReviewedOn(), p.getBiaReviewMonths(), active && ContinuityRules.biaOverdue(p.getBiaReviewedOn(), p.getBiaReviewMonths(), recordedOn, today),
                plan == null ? null : plan.getId(), plan == null ? null : plan.getReference(), plan == null ? null : plan.getVersionLabel(),
                plan == null ? null : plan.getNextReviewDue(), active && plan != null && ContinuityRules.planReviewOverdue(plan.getNextReviewDue(), today),
                lastDate, last == null ? null : last.getOutcome(), last == null ? null : last.rtoMet(),
                active && plan != null ? ContinuityRules.testDueOn(p.getCriticality(), lastDate, approvedOn) : null,
                active && plan != null && ContinuityRules.testOverdue(p.getCriticality(), lastDate, approvedOn, today), p.getRetiredReason());
    }

    private ProcessDetail detail(TenantId t, BcProcess p) {
        Snapshot s = snapshot(t);
        List<BcPlan> processPlans = plans.findForProcess(t, p.getId());
        Map<UUID, BcPlan> byId = processPlans.stream().collect(Collectors.toMap(BcPlan::getId, x -> x));
        LocalDate today = today();
        List<PlanResponse> planResponses = processPlans.stream().map(x -> new PlanResponse(x.getId(), x.getReference(), p.getId(), p.getName(), x.getTitle(), x.getVersionLabel(), x.getOwnerName(),
                x.getContent(), x.getStatus(), x.getReviewMonths(), x.getAuthorName(), x.getApprovedByName(), x.getApprovedOn(), x.getNextReviewDue(), x.getLastReviewedOn(),
                x.getStatus() == BcPlanStatus.APPROVED && ContinuityRules.planReviewOverdue(x.getNextReviewDue(), today), x.getRetiredReason())).toList();
        List<TestResponse> testResponses = byId.isEmpty() ? List.of() : tests.findForPlans(t, byId.keySet()).stream().map(x -> new TestResponse(x.getId(), x.getPlanId(), byId.get(x.getPlanId()).getReference(),
                x.getTestDate(), x.getTestType(), x.getOutcome(), x.getRtoTargetHours(), x.getAchievedRtoHours(), x.rtoMet(), x.getIssues(), x.getActions(), x.getConductedByName(), x.getRecordedByName())).toList();
        return new ProcessDetail(response(p, s), planResponses, testResponses);
    }

    private BcProcess loadProcess(TenantId t, UUID id) { return processes.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Process", id.toString())); }
    private BcPlan loadPlan(TenantId t, UUID id) { return plans.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Plan", id.toString())); }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
