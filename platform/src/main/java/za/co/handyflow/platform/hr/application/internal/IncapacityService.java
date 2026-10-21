package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static za.co.handyflow.platform.hr.application.internal.ErCaseSupport.*;

/**
 * Incapacity: poor performance and ill health. The case records the steps the Code of Good Practice: Dismissal expects, the meeting with the
 * employee, and the decision. A dismissal for incapacity is blocked until the steps are recorded.
 */
@Service
@RequiredArgsConstructor
public class IncapacityService {

    private final HrErCaseRepository caseRepo;
    private final HrErStepRepository stepRepo;
    private final ErCaseSupport support;
    private final ErCaseViews views;
    private final ErPolicyService policyService;

    private static final Set<String> BEFORE_DECISION = Set.of(ErRules.REPORTED, ErRules.INVESTIGATING, ErRules.NOTICE_ISSUED, ErRules.HEARING_HELD);

    /** Ticks a step done, or takes the tick away. */
    @Transactional
    public Detail step(TenantId tenantId, UUID id, StepRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.INCAPACITY);
        if (!BEFORE_DECISION.contains(c.getStatus())) throw new IllegalStateException("Steps can only be changed before the decision.");
        HrErStep step = stepRepo.forCase(c.getId()).stream().filter(s -> s.getCode().equals(req.code())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Step", String.valueOf(req.code())));
        if (Boolean.TRUE.equals(req.undo())) {
            step.undo();
            stepRepo.save(step);
            support.event(c, "STEP_UNDONE", step.getLabel(), false, userId);
        } else {
            fail(IncapacityRules.stepProblem(c.getCategory(), req.code(), req.doneOn(), support.today()));
            if (req.note() != null && req.note().length() > 1000) throw new IllegalArgumentException("The note is too long (1000 characters at most).");
            step.done(req.doneOn(), userId, req.note() == null || req.note().isBlank() ? null : req.note().trim());
            stepRepo.save(step);
            support.event(c, "STEP_DONE", step.getLabel(), false, userId);
        }
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** The period the employee is given to improve. Only for poor performance. */
    @Transactional
    public Detail review(TenantId tenantId, UUID id, ReviewRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.INCAPACITY);
        if (!"POOR_PERFORMANCE".equals(c.getCategory())) throw new IllegalStateException("A review period applies to poor performance only.");
        if (!BEFORE_DECISION.contains(c.getStatus())) throw new IllegalStateException("The review period can only be changed before the decision.");
        fail(IncapacityRules.reviewProblem(req.from(), req.to()));
        c.setReview(req.from(), req.to());
        caseRepo.save(c);
        support.event(c, "REVIEW_SET", "Review period " + req.from() + " to " + req.to(), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** More needs to be looked into after the meeting. The case goes back to investigation. */
    @Transactional
    public Detail returnToInvestigation(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.INCAPACITY);
        fail(ErRules.textProblem("The reason", req.reason(), 500));
        support.move(c, ErRules.INVESTIGATING);
        c.moveTo(ErRules.INVESTIGATING);
        caseRepo.save(c);
        support.event(c, "RETURNED_TO_INVESTIGATION", req.reason().trim(), false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    @Transactional
    public Detail decide(TenantId tenantId, UUID id, IncapacityDecisionRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.INCAPACITY);
        support.move(c, ErRules.DECIDED);
        support.employee(tenantId, req.deciderId());
        fail(DisciplineRules.chairProblem(req.deciderId(), c.getEmployeeId(), null));
        fail(IncapacityRules.outcomeProblem(c.getCategory(), req.outcome(), support.doneSteps(c), c.getReviewFrom(), c.getReviewTo(),
                support.today(), policyService.get(tenantId), req.note()));
        LocalDate on = date(req.decidedOn(), c.getHearingHeldOn());
        c.setParties(c.getInitiatorId(), req.deciderId(), c.getRespondentId());
        c.decide(req.outcome(), on, req.note().trim(), userId, null, null);
        caseRepo.save(c);
        support.event(c, "DECIDED", words(req.outcome()), false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** An appeal can confirm the decision or replace it with a lighter one. It cannot turn a decision into a dismissal. */
    @Transactional
    public Detail decideAppeal(TenantId tenantId, UUID id, AppealDecisionRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.INCAPACITY);
        if (!ErRules.APPEALED.equals(c.getStatus())) throw new IllegalStateException("There is no appeal waiting for a decision.");
        support.employee(tenantId, req.deciderId());
        fail(DisciplineRules.appealDeciderProblem(req.deciderId(), c.getEmployeeId(), c.getChairId(), null));
        fail(ErRules.textProblem("The reasons for the appeal decision", req.note(), 4000));
        String outcome = req.outcome();
        String replacement = null;
        if ("VARIED".equals(outcome)) {
            replacement = req.newOutcome();
            if (replacement == null || !IncapacityRules.outcomes(c.getCategory()).contains(replacement))
                throw new IllegalArgumentException("Choose the outcome that replaces the original.");
            if (replacement.equals(c.getOutcome())) throw new IllegalArgumentException("The replacement must differ from the original outcome.");
            if (IncapacityRules.DISMISSAL.equals(replacement)) throw new IllegalArgumentException("An appeal cannot lead to a dismissal.");
        } else if (!"CONFIRMED".equals(outcome)) {
            throw new IllegalArgumentException("Choose the appeal outcome: confirmed or varied.");
        }
        LocalDate on = date(req.decidedOn(), c.getAppealLodgedOn());
        c.decideAppeal(req.deciderId(), on, outcome, req.note().trim(), replacement, null);
        caseRepo.save(c);
        support.event(c, "APPEAL_DECIDED", "Appeal " + outcome.toLowerCase() + (replacement == null ? "" : ": " + words(replacement)), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    private LocalDate date(LocalDate given, LocalDate notBefore) {
        LocalDate today = support.today();
        LocalDate on = given == null ? today : given;
        if (on.isAfter(today)) throw new IllegalArgumentException("The decision cannot be dated in the future.");
        if (notBefore != null && on.isBefore(notBefore)) throw new IllegalArgumentException("The decision cannot come before " + notBefore + ".");
        return on;
    }
}
