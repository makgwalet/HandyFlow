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

import java.util.*;

/**
 * 360 feedback. The reviewer or HR asks colleagues about an employee in a review; colleagues answer from their own list of requests. The
 * employee and the reviewer see only a summary, and only once enough people have replied for nobody to be picked out (see
 * {@link PerfRules#MIN_REPLIES}). HR can see who said what.
 */
@Service
@RequiredArgsConstructor
public class PerfFeedbackService {

    private final HrPerfFeedbackRepository feedbackRepo;
    private final HrPerfReviewRepository reviewRepo;
    private final HrPerfCycleRepository cycleRepo;
    private final PerfSupport support;

    /** Asks people for feedback. Everyone in the list is checked before anyone is asked. */
    @Transactional
    public List<FeedbackView> request(TenantId tenantId, UUID reviewId, FeedbackRequestBody req, UUID userId, Set<String> held) {
        PerfSupport.ReviewCtx x = support.review(tenantId, reviewId, held, userId);
        HrPerfReview r = x.review();
        List<String> actions = PerfRules.reviewActions(x.actionWho(), r.getStatus(), x.cycle().getStatus(), x.cycle().isInclude360(), r.getReviewerId() != null,
                x.cycle().getSelfDue(), support.today());
        PerfSupport.require(actions, PerfRules.A_FEEDBACK, x.actionWho().hr() || x.actionWho().reviewer(),
                "Only the reviewer or HR can ask for feedback.", "Feedback can be asked for while a 360 review is under way.");
        List<UUID> wanted = req.providerIds() == null ? List.of() : req.providerIds().stream().distinct().toList();
        if (wanted.isEmpty()) throw new IllegalArgumentException("Choose who to ask.");
        List<HrPerfFeedback> existing = feedbackRepo.forReview(r.getId());
        Set<UUID> asked = new HashSet<>();
        for (HrPerfFeedback f : existing) asked.add(f.getProviderId());
        int count = existing.size();
        List<HrPerfFeedback> fresh = new ArrayList<>();
        for (UUID p : wanted) {
            PerfSupport.fail(PerfRules.requestProblem(p, r.getEmployeeId(), r.getReviewerId(), asked.contains(p), count, req.relationship()));
            support.serving(tenantId, p);
            fresh.add(HrPerfFeedback.request(tenantId.getValue(), r.getId(), p, req.relationship(), userId));
            count++;
        }
        for (HrPerfFeedback f : fresh) feedbackRepo.save(f);
        support.event(tenantId, HrPerfEvent.REVIEW, r.getId(), "FEEDBACK_REQUESTED", fresh.size() + (fresh.size() == 1 ? " person asked" : " people asked"), userId);
        return requests(r.getId(), true, PerfRules.sight(x.sightWho(), r.getStatus()).feedbackNames());
    }

    /** What the signed-in person has been asked to give feedback on. */
    @Transactional(readOnly = true)
    public List<FeedbackTask> mine(TenantId tenantId, UUID userId) {
        HrEmployee me = support.me(tenantId, userId).orElse(null);
        if (me == null) return List.of();
        List<FeedbackTask> out = new ArrayList<>();
        for (HrPerfFeedback f : feedbackRepo.forProvider(tenantId, me.getId())) {
            if (!"REQUESTED".equals(f.getStatus())) continue;
            HrPerfReview r = reviewRepo.findByTenantAndId(tenantId, f.getReviewId()).orElse(null);
            if (r == null || !open(r)) continue;
            HrPerfCycle c = cycleRepo.findByTenantAndId(tenantId, r.getCycleId()).orElse(null);
            if (c == null || !PerfRules.OPEN.equals(c.getStatus())) continue;
            HrEmployee about = support.employee(tenantId, r.getEmployeeId());
            out.add(new FeedbackTask(f.getId(), about.getFullName(), about.getJobTitle(), c.getName(), f.getRelationship(), f.getStatus(), c.getManagerDue(), f.getRequestedAt()));
        }
        return out;
    }

    @Transactional
    public FeedbackTask submit(TenantId tenantId, UUID id, FeedbackSubmit req, UUID userId) {
        Mine m = mineToAnswer(tenantId, id, userId);
        PerfSupport.fail(PerfRules.feedbackProblem(req.rating(), req.strengths(), req.improvements()));
        m.feedback().submit(req.rating(), req.strengths(), req.improvements());
        feedbackRepo.save(m.feedback());
        return task(tenantId, m, m.feedback());
    }

    @Transactional
    public FeedbackTask decline(TenantId tenantId, UUID id, ReasonRequest req, UUID userId) {
        Mine m = mineToAnswer(tenantId, id, userId);
        PerfSupport.fail(PerfRules.declineProblem(req.reason()));
        m.feedback().decline(req.reason());
        feedbackRepo.save(m.feedback());
        return task(tenantId, m, m.feedback());
    }

    // ── For the review screen ─────────────────────────────────────────────────────────────────────

    /** The summary, or just how many have replied while there are too few. */
    SummaryView summary(UUID reviewId) {
        List<HrPerfFeedback> all = feedbackRepo.forReview(reviewId);
        List<PerfRules.Reply> replies = new ArrayList<>();
        int declined = 0;
        for (HrPerfFeedback f : all) {
            if ("SUBMITTED".equals(f.getStatus())) replies.add(new PerfRules.Reply(f.getRating(), f.getStrengths(), f.getImprovements()));
            else if ("DECLINED".equals(f.getStatus())) declined++;
        }
        PerfRules.Summary s = PerfRules.summarise(replies);
        return new SummaryView(s.shown(), s.replies(), s.average(), s.strengths(), s.improvements(), all.size(), declined, PerfRules.MIN_REPLIES);
    }

    /** Who was asked, and whether they have replied. What they wrote is included only when {@code content} is true (HR). */
    List<FeedbackView> requests(UUID reviewId, boolean names, boolean content) {
        List<HrPerfFeedback> all = feedbackRepo.forReview(reviewId);
        Map<UUID, String> people = names ? support.names(all.stream().map(HrPerfFeedback::getProviderId).toList()) : Map.of();
        List<FeedbackView> out = new ArrayList<>();
        for (HrPerfFeedback f : all) {
            out.add(new FeedbackView(f.getId(), f.getRelationship(), f.getStatus(), names ? people.getOrDefault(f.getProviderId(), "Unknown") : null,
                    f.getRequestedAt(), f.getRespondedAt(), content ? f.getRating() : null, content ? f.getStrengths() : null, content ? f.getImprovements() : null));
        }
        return out;
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────

    private record Mine(HrPerfFeedback feedback, HrPerfReview review, HrPerfCycle cycle) {}

    private Mine mineToAnswer(TenantId tenantId, UUID id, UUID userId) {
        HrEmployee me = support.requireMe(tenantId, userId);
        HrPerfFeedback f = feedbackRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Feedback request", String.valueOf(id)));
        if (!me.getId().equals(f.getProviderId())) throw new ResourceNotFoundException("Feedback request", String.valueOf(id));
        if (!"REQUESTED".equals(f.getStatus())) throw new IllegalStateException("You have already answered this request.");
        HrPerfReview r = reviewRepo.findByTenantAndId(tenantId, f.getReviewId()).orElseThrow(() -> new ResourceNotFoundException("Review", String.valueOf(f.getReviewId())));
        HrPerfCycle c = cycleRepo.findByTenantAndId(tenantId, r.getCycleId()).orElseThrow(() -> new ResourceNotFoundException("Cycle", String.valueOf(r.getCycleId())));
        if (!open(r) || !PerfRules.OPEN.equals(c.getStatus())) throw new IllegalStateException("This review is no longer taking feedback.");
        if (me.getId().equals(r.getEmployeeId())) throw new AccessDeniedException("You cannot give feedback about yourself.");
        return new Mine(f, r, c);
    }

    private FeedbackTask task(TenantId tenantId, Mine m, HrPerfFeedback f) {
        HrEmployee about = support.employee(tenantId, m.review().getEmployeeId());
        return new FeedbackTask(f.getId(), about.getFullName(), about.getJobTitle(), m.cycle().getName(), f.getRelationship(), f.getStatus(), m.cycle().getManagerDue(), f.getRequestedAt());
    }

    private static boolean open(HrPerfReview r) {
        return PerfRules.AWAITING_SELF.equals(r.getStatus()) || PerfRules.AWAITING_MANAGER.equals(r.getStatus()) || PerfRules.SUBMITTED.equals(r.getStatus());
    }
}
