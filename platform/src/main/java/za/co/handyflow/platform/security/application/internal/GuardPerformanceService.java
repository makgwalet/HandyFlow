// security/application/internal/GuardPerformanceService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.Finding;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.Status;
import za.co.handyflow.platform.security.domain.model.GuardComplaint;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.ShiftStatus;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.GuardComplaintRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Builds the performance view of a guard: the operational score with its working, and the risk recommendations.
 * Everything is computed from existing records on request, nothing is stored, so the explanation always matches
 * the data. The recommendations are for a person to review; nothing here changes a guard's status.
 */
@Service
@RequiredArgsConstructor
public class GuardPerformanceService {

    static final int SCORE_WINDOW_DAYS = 90;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardService guardService;
    private final GuardOverviewService overviewService;
    private final GuardRatingService ratingService;
    private final GuardRiskSettingsService settingsService;
    private final ShiftRepository shiftRepository;
    private final CheckpointLogRepository checkpointLogRepository;
    private final IncidentRepository incidentRepository;
    private final GuardComplaintRepository complaintRepository;

    @Transactional(readOnly = true)
    public PerformanceResponse performance(TenantId tenantId, UUID guardId) { return performance(tenantId, guardId, Instant.now()); }

    @Transactional(readOnly = true)
    PerformanceResponse performance(TenantId tenantId, UUID guardId, Instant now) {
        guardService.getGuard(tenantId, guardId);
        LocalDate today = now.atZone(SAST).toLocalDate();
        var settings = settingsService.effective(tenantId);

        // Shifts: the score window, judged on shifts that have already started.
        List<Shift> shifts = shiftRepository.findByGuardInRange(tenantId, guardId, now.minus(Duration.ofDays(SCORE_WINDOW_DAYS)), now);
        int completed = 0, pulled = 0, missed = 0, late = 0, patrolRequired = 0, patrolMet = 0;
        for (Shift s : shifts) {
            ShiftStatus st = s.getStatus();
            if (st == ShiftStatus.COMPLETED) completed++;
            else if (st == ShiftStatus.PULLED) pulled++;
            else if (st == ShiftStatus.MISSED) missed++;
            if ((st == ShiftStatus.COMPLETED || st == ShiftStatus.PULLED) && ShiftPunctuality.late(s.getStartAt(), s.getActualStartAt(), s.getLateAlertSentAt())) late++;
            if (st == ShiftStatus.COMPLETED && s.getMinScanCount() > 0) {
                patrolRequired++;
                if (checkpointLogRepository.countByShiftId(s.getId()) >= s.getMinScanCount()) patrolMet++;
            }
        }

        // Incidents: enough history for both the score and the critical-incident rule.
        int incidentDays = Math.max(SCORE_WINDOW_DAYS, settings.windowDays());
        List<Incident> incidents = incidentRepository.findByGuardInRange(tenantId, guardId, now.minus(Duration.ofDays(incidentDays)), now);
        Instant scoreFrom = now.minus(Duration.ofDays(SCORE_WINDOW_DAYS));
        Instant riskFrom = now.minus(Duration.ofDays(settings.windowDays()));
        int low = 0, med = 0, high = 0, crit = 0, criticalInRiskWindow = 0;
        for (Incident i : incidents) {
            String sev = i.getSeverity() == null ? "" : i.getSeverity().toUpperCase();
            if (!i.getCreatedAt().isBefore(scoreFrom)) {
                switch (sev) { case "LOW" -> low++; case "MEDIUM" -> med++; case "HIGH" -> high++; case "CRITICAL" -> crit++; default -> med++; }
            }
            if ("CRITICAL".equals(sev) && !i.getCreatedAt().isBefore(riskFrom)) criticalInRiskWindow++;
        }

        // Complaints: those that stand (not withdrawn, not found unsubstantiated).
        List<GuardComplaint> complaints = complaintRepository.findForGuard(tenantId, guardId);
        LocalDate scoreSince = today.minusDays(SCORE_WINDOW_DAYS), riskSince = today.minusDays(settings.windowDays()),
                misconductSince = today.minusDays(settings.misconductWindowDays());
        int substantiatedScore = 0, counted = 0, misconduct = 0, openUrgent = 0;
        for (GuardComplaint c : complaints) {
            boolean stands = c.getStatus() != Status.WITHDRAWN && c.getFinding() != Finding.UNSUBSTANTIATED;
            if (stands && !c.getOccurredOn().isBefore(riskSince)) counted++;
            if (c.getFinding() == Finding.SUBSTANTIATED && c.getStatus() != Status.WITHDRAWN) {
                if (!c.getOccurredOn().isBefore(scoreSince)) substantiatedScore++;
                if (!c.getOccurredOn().isBefore(misconductSince) && ComplaintWorkflow.isMisconduct(c.getCategory())) misconduct++;
            }
            if (ComplaintWorkflow.isOpen(c.getStatus()) && ComplaintWorkflow.isUrgent(c.getCategory(), c.getSeverity())) openUrgent++;
        }

        var ratings = ratingService.recent(tenantId, guardId, today);
        double ratingAvg = ratings.isEmpty() ? 0 : ratings.stream().mapToDouble(RatingItem::average).average().orElse(0);
        Integer readiness = overviewService.overview(tenantId, guardId, now).readiness().percent();

        var result = GuardScoreCalculator.calculate(new GuardScoreCalculator.Facts(completed, pulled, missed, late, patrolRequired, patrolMet,
                low, med, high, crit, substantiatedScore, readiness, ratings.size(), ratingAvg));
        var recs = GuardRiskEngine.evaluate(new GuardRiskEngine.Facts(counted, misconduct, criticalInRiskWindow, openUrgent), settings);

        return new PerformanceResponse(result.score(), result.band(), result.coverage(),
                result.components().stream().map(c -> new ScoreComponent(c.key(), c.label(), c.weight(), c.hasData(),
                        (int) Math.round(c.fraction() * 100), Math.round(c.points() * 10) / 10.0, c.detail())).toList(),
                recs.stream().map(r -> new RecommendationItem(r.code(), r.level(), r.title(), r.reason())).toList(),
                new Basis(settings.windowDays(), counted, misconduct, criticalInRiskWindow, openUrgent),
                GuardRiskSettingsService.dto(settings, false, null, null),
                ratings.isEmpty() ? null : Math.round(ratingAvg * 10) / 10.0, ratings.size(), ratings);
    }
}
