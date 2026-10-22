package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Predicate;

/** Turns reviews into what the screens show, applying what each person is allowed to see. */
@Component
@RequiredArgsConstructor
class PerfReviewViews {

    private final HrPerfCycleRepository cycleRepo;
    private final PerfSupport support;
    private final PerfGoalService goals;
    private final PerfFeedbackService feedback;

    /** Lines for a list. {@code showRating} says, for each review, whether the viewer may see its rating. */
    List<ReviewRow> rows(TenantId tenantId, List<HrPerfReview> reviews, Predicate<HrPerfReview> showRating) {
        Set<UUID> cycleIds = new HashSet<>();
        List<UUID> people = new ArrayList<>();
        for (HrPerfReview r : reviews) { cycleIds.add(r.getCycleId()); people.add(r.getEmployeeId()); people.add(r.getReviewerId()); }
        Map<UUID, HrPerfCycle> cycles = new HashMap<>();
        if (!cycleIds.isEmpty()) for (HrPerfCycle c : cycleRepo.findAllById(cycleIds)) cycles.put(c.getId(), c);
        Map<UUID, String> names = support.names(people);
        List<ReviewRow> out = new ArrayList<>();
        for (HrPerfReview r : reviews) {
            HrPerfCycle c = cycles.get(r.getCycleId());
            if (c == null) continue;
            out.add(new ReviewRow(r.getId(), r.getCycleId(), c.getName(), r.getEmployeeId(), names.getOrDefault(r.getEmployeeId(), "Unknown"), r.getJobTitle(),
                    r.getReviewerId(), r.getReviewerId() == null ? null : names.getOrDefault(r.getReviewerId(), "Unknown"), r.getStatus(), c.getSelfDue(), c.getManagerDue(),
                    showRating.test(r) ? r.getFinalRating() : null, r.getSharedAt(), r.getAckAt()));
        }
        out.sort(Comparator.comparing(ReviewRow::employeeName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** One review, with only what this person may read. */
    ReviewDetail detail(TenantId tenantId, PerfSupport.ReviewCtx x, Set<String> held) {
        HrPerfReview r = x.review();
        HrPerfCycle c = x.cycle();
        PerfRules.Sight sight = PerfRules.sight(x.sightWho(), r.getStatus());
        boolean subject = x.isSubject();
        ReviewRow row = rows(tenantId, List.of(r), rr -> sight.manager()).get(0);
        SummaryView summary = c.isInclude360() && sight.feedback() ? feedback.summary(r.getId()) : null;
        List<FeedbackView> requests = c.isInclude360() && !subject && sight.feedback() ? feedback.requests(r.getId(), true, sight.feedbackNames()) : List.of();
        List<String> actions = PerfRules.reviewActions(x.actionWho(), r.getStatus(), c.getStatus(), c.isInclude360(), r.getReviewerId() != null, c.getSelfDue(), support.today());
        return new ReviewDetail(row, c.isInclude360(), c.getPeriodStart(), c.getPeriodEnd(),
                sight.self() ? r.getSelfRating() : null, sight.self() ? r.getSelfComment() : null, sight.self() ? r.getSelfAt() : null,
                sight.manager() ? r.getMgrRating() : null, sight.manager() ? r.getMgrComment() : null, sight.manager() ? r.getMgrAt() : null,
                sight.manager() ? r.getFinalRating() : null, !subject && r.isCalibrated(), subject ? null : r.getCalibrationReason(),
                r.getEmployeeComment(), r.getExcludedReason(), summary, requests,
                goals.forReview(tenantId, c, x.subject(), x.me(), held),
                subject ? List.of() : support.events(tenantId, HrPerfEvent.REVIEW, r.getId()), actions, PerfRules.RATING_LABELS);
    }
}
