package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Reviews. The employee writes a self-review, the manager rates and says why, HR may adjust the final rating with a reason, then releases
 * the result. The employee reads it and acknowledges, with a reply if they wish. Once released, nothing in it can change.
 */
@Service
@RequiredArgsConstructor
public class PerfReviewService {

    private static final int LIST_LIMIT = 1000;

    private final HrPerfReviewRepository reviewRepo;
    private final HrEmployeeRepository employeeRepo;
    private final PerfSupport support;
    private final PerfReviewViews views;
    private final PerfNotifier notifier;

    /** Where the signed-in person sits: their employee record, how many people report to them, and what they hold. */
    @Transactional(readOnly = true)
    public Me me(TenantId tenantId, UUID userId, Set<String> held) {
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        int reports = 0;
        if (me != null) for (HrEmployee e : employeeRepo.findNotTerminated(tenantId)) if (me.getId().equals(e.getManagerId())) reports++;
        return new Me(me == null ? null : me.getId(), me == null ? null : me.getFullName(), reports, PerfRules.canRead(held), PerfRules.canManage(held));
    }

    /** Reviews as "mine" (about me), "team" (I am the reviewer) or "all" (HR_PERF_READ), optionally for one cycle. */
    @Transactional(readOnly = true)
    public List<ReviewRow> reviews(TenantId tenantId, UUID cycleId, String scope, Set<String> held, UUID userId) {
        HrEmployee me = "all".equals(scope) ? support.me(tenantId, userId).orElse(null) : support.requireMe(tenantId, userId);
        List<HrPerfReview> found;
        if ("all".equals(scope)) {
            PerfSupport.requireRead(held);
            if (cycleId == null) throw new IllegalArgumentException("Choose a cycle.");
            found = reviewRepo.forCycle(tenantId, cycleId);
        } else if ("team".equals(scope)) {
            found = reviewRepo.forReviewer(tenantId, me.getId());
        } else {
            found = reviewRepo.forEmployee(tenantId, me.getId());
        }
        List<HrPerfReview> list = found.stream().filter(r -> cycleId == null || cycleId.equals(r.getCycleId())).limit(LIST_LIMIT).toList();
        UUID myId = me == null ? null : me.getId();
        return views.rows(tenantId, list, r -> !r.getEmployeeId().equals(myId) || PerfRules.SHARED.equals(r.getStatus()) || PerfRules.ACKNOWLEDGED.equals(r.getStatus()));
    }

    @Transactional(readOnly = true)
    public ReviewDetail get(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        return views.detail(tenantId, support.review(tenantId, id, held, userId), held);
    }

    @Transactional
    public ReviewDetail submitSelf(TenantId tenantId, UUID id, SelfRequest req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        require(x, PerfRules.A_SELF, x.actionWho().subject(), "Only the employee can write their own review.", "This review is not waiting for a self-review.");
        PerfSupport.fail(PerfRules.selfProblem(req.rating(), req.comment()));
        HrPerfReview r = x.review();
        r.submitSelf(req.rating(), req.comment());
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "SELF_SUBMITTED", null, userId);
        notifier.readyForReviewer(tenantId, x.cycle(), r, x.subject());
        return views.detail(tenantId, x, held);
    }

    @Transactional
    public ReviewDetail submitManager(TenantId tenantId, UUID id, ManagerRequest req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        HrPerfReview r = x.review();
        if (x.actionWho().reviewer() && PerfRules.AWAITING_SELF.equals(r.getStatus()) && !support.today().isAfter(x.cycle().getSelfDue()))
            throw new IllegalStateException("Wait for the employee's self-review, or for its due date (" + x.cycle().getSelfDue() + ").");
        require(x, PerfRules.A_MANAGER, x.actionWho().reviewer(), "Only the employee's reviewer can rate this review.", "This review is not waiting for the manager.");
        PerfSupport.fail(PerfRules.managerProblem(req.rating(), req.comment()));
        r.submitManager(req.rating(), req.comment(), userId);
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "MANAGER_SUBMITTED", null, userId);
        return views.detail(tenantId, x, held);
    }

    @Transactional
    public ReviewDetail setReviewer(TenantId tenantId, UUID id, ReviewerRequest req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        require(x, PerfRules.A_REVIEWER, x.actionWho().hr(), "Only HR can change who reviews.", "The reviewer can be changed until they have submitted their rating.");
        HrPerfReview r = x.review();
        PerfSupport.fail(PerfRules.reviewerProblem(req.reviewerId(), r.getEmployeeId(), req.reason()));
        HrEmployee next = support.serving(tenantId, req.reviewerId());
        r.setReviewer(next.getId());
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "REVIEWER_CHANGED", next.getFullName() + " – " + req.reason().trim(), userId);
        return views.detail(tenantId, support.review(tenantId, id, held, userId), held);
    }

    @Transactional
    public ReviewDetail calibrate(TenantId tenantId, UUID id, CalibrateRequest req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        require(x, PerfRules.A_CALIBRATE, x.actionWho().hr(), "Only HR can change the final rating.", "The final rating can be changed after the manager has rated and before the result is released.");
        HrPerfReview r = x.review();
        PerfSupport.fail(PerfRules.calibrationProblem(req.rating(), req.reason(), r.getFinalRating()));
        Integer was = r.getFinalRating();
        r.calibrate(req.rating(), req.reason());
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "CALIBRATED", "From " + was + " to " + req.rating() + " – " + req.reason().trim(), userId);
        return views.detail(tenantId, x, held);
    }

    @Transactional
    public ReviewDetail share(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        require(x, PerfRules.A_SHARE, x.actionWho().hr(), "Only HR can release a result.", "A result can be released after the manager has rated.");
        HrPerfReview r = x.review();
        r.share(userId);
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "SHARED", null, userId);
        notifier.resultReleased(tenantId, x.cycle(), r, x.subject());
        return views.detail(tenantId, x, held);
    }

    @Transactional
    public ReviewDetail exclude(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        require(x, PerfRules.A_EXCLUDE, x.actionWho().hr(), "Only HR can leave a review out.", "A review that has been released cannot be left out.");
        PerfSupport.fail(PerfRules.excludeProblem(req.reason()));
        HrPerfReview r = x.review();
        r.exclude(req.reason());
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "EXCLUDED", req.reason().trim(), userId);
        return views.detail(tenantId, x, held);
    }

    @Transactional
    public ReviewDetail acknowledge(TenantId tenantId, UUID id, AcknowledgeRequest req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, id, held, userId);
        require(x, PerfRules.A_ACKNOWLEDGE, x.actionWho().subject(), "Only the employee can acknowledge their result.", "There is no released result to acknowledge.");
        PerfSupport.fail(PerfRules.acknowledgeProblem(req == null ? null : req.comment()));
        HrPerfReview r = x.review();
        r.acknowledge(userId, req == null ? null : req.comment());
        reviewRepo.save(r);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "ACKNOWLEDGED", null, userId);
        return views.detail(tenantId, x, held);
    }

    private void require(PerfSupport.ReviewCtx x, String action, boolean roleOk, String denied, String state) {
        List<String> actions = PerfRules.reviewActions(x.actionWho(), x.review().getStatus(), x.cycle().getStatus(), x.cycle().isInclude360(),
                x.review().getReviewerId() != null, x.cycle().getSelfDue(), support.today());
        PerfSupport.require(actions, action, roleOk, denied, state);
    }
}
