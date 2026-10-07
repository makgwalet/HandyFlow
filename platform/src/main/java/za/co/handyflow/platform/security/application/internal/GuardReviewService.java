// security/application/internal/GuardReviewService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.GuardReview;
import za.co.handyflow.platform.security.domain.repository.GuardReviewRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.SaveRatingRequest;
import za.co.handyflow.platform.security.dto.GuardReviewDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supervisor reviews of guards. Saving a review also records a SUPERVISOR rating with the same six scores, so the
 * review counts towards the operational score through the ratings component. Reviews are never edited or deleted.
 */
@Service
@RequiredArgsConstructor
public class GuardReviewService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardReviewRepository repository;
    private final GuardService guardService;
    private final GuardRatingService ratingService;
    private final SiteRepository siteRepository;

    @Transactional(readOnly = true)
    public ReviewList list(TenantId tenantId, UUID guardId) { return list(tenantId, guardId, LocalDate.now(SAST)); }

    @Transactional(readOnly = true)
    ReviewList list(TenantId tenantId, UUID guardId, LocalDate today) {
        guardService.getGuard(tenantId, guardId);
        Map<UUID, String> sites = new HashMap<>();
        List<GuardReview> all = repository.findForGuard(tenantId, guardId);
        List<ReviewItem> items = all.stream().map(r -> toItem(r, sites, tenantId)).toList();
        GuardReview latest = all.isEmpty() ? null : all.get(0);
        LocalDate last = latest == null ? null : latest.getReviewDate();
        Integer days = last == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(last, today);
        return new ReviewList(items, last, days, GuardReviewRules.dueState(last, latest == null ? null : latest.getFollowUpDate(), today),
                GuardReviewRules.INTERVAL_DAYS);
    }

    @Transactional
    public ReviewItem add(TenantId tenantId, UUID guardId, SaveReviewRequest req, UUID by, String byName) {
        return add(tenantId, guardId, req, by, byName, LocalDate.now(SAST));
    }

    @Transactional
    ReviewItem add(TenantId tenantId, UUID guardId, SaveReviewRequest req, UUID by, String byName, LocalDate today) {
        guardService.getGuard(tenantId, guardId);
        if (req.siteId() != null) siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", req.siteId().toString()));
        GuardReview.Overall overall;
        try { overall = GuardReview.Overall.valueOf(req.overall().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw bad("Unknown overall assessment: " + req.overall(), "INVALID_OVERALL"); }
        if (req.periodTo().isBefore(req.periodFrom())) throw bad("The period cannot end before it starts", "INVALID_PERIOD");
        if (req.periodTo().isAfter(today) || req.reviewDate().isAfter(today)) throw bad("A review cannot be dated in the future", "INVALID_DATE");
        if (req.reviewDate().isBefore(req.periodTo())) throw bad("The review date cannot be before the end of the period", "INVALID_DATE");
        if (req.followUpDate() != null && req.followUpDate().isBefore(req.reviewDate())) throw bad("The follow-up date cannot be before the review", "INVALID_FOLLOW_UP");
        if (isBlank(req.strengths()) && isBlank(req.improvements())) throw bad("Say what went well or what needs to improve", "COMMENT_REQUIRED");
        if (isBlank(byName)) throw bad("The reviewer's name is needed", "REVIEWER_REQUIRED");
        int[] scores = {req.punctuality(), req.professionalism(), req.appearance(), req.communication(), req.alertness(), req.incidentHandling()};
        for (int s : scores) if (s < 1 || s > 5) throw bad("Each score must be from 1 to 5", "INVALID_SCORE");

        // The rating is saved first; both live in one transaction, so a failure leaves neither.
        var rating = ratingService.add(tenantId, guardId, new SaveRatingRequest(req.siteId(), "SUPERVISOR", byName.trim(), req.reviewDate(),
                scores[0], scores[1], scores[2], scores[3], scores[4], scores[5], summaryComment(overall, req)), by, byName);
        GuardReview saved = repository.save(GuardReview.create(tenantId, guardId, req.siteId(), req.reviewDate(), req.periodFrom(), req.periodTo(),
                byName.trim(), overall, scores, req.strengths(), req.improvements(), req.trainingNeeds(), req.actionsAgreed(), req.followUpDate(),
                rating.id(), by, byName));
        return toItem(saved, new HashMap<>(), tenantId);
    }

    private static String summaryComment(GuardReview.Overall overall, SaveReviewRequest req) {
        String label = switch (overall) { case EXCEEDS -> "Exceeds expectations"; case MEETS -> "Meets expectations"; case BELOW -> "Below expectations"; };
        String text = !isBlank(req.improvements()) ? req.improvements().trim() : req.strengths().trim();
        return "Supervisor review: " + label + ". " + (text.length() > 300 ? text.substring(0, 300) + "..." : text);
    }

    private ReviewItem toItem(GuardReview r, Map<UUID, String> sites, TenantId tenantId) {
        String site = r.getSiteId() == null ? null
                : sites.computeIfAbsent(r.getSiteId(), k -> siteRepository.findActiveById(tenantId, k).map(s -> s.getName()).orElse(null));
        return new ReviewItem(r.getId(), r.getSiteId(), site, r.getReviewDate(), r.getPeriodFrom(), r.getPeriodTo(), r.getReviewerName(),
                r.getOverall().name(), r.getPunctuality(), r.getProfessionalism(), r.getAppearance(), r.getCommunication(), r.getAlertness(),
                r.getIncidentHandling(), Math.round(r.average() * 10) / 10.0, r.getStrengths(), r.getImprovements(), r.getTrainingNeeds(),
                r.getActionsAgreed(), r.getFollowUpDate(), r.getCreatedAt());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static HandyFlowException bad(String message, String code) { return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code); }
}
