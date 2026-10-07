// security/dto/GuardPerformanceDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shapes for ratings, risk thresholds and the performance view of a guard. */
public final class GuardPerformanceDtos {

    private GuardPerformanceDtos() {}

    public record SaveRatingRequest(
            UUID siteId, @NotBlank String source, String raterName, @NotNull LocalDate ratedOn,
            int punctuality, int professionalism, int appearance, int communication, int alertness, int incidentHandling,
            String comment) {}

    public record RatingItem(
            UUID id, String source, String raterName, UUID siteId, String siteName, LocalDate ratedOn,
            int punctuality, int professionalism, int appearance, int communication, int alertness, int incidentHandling,
            double average, String comment, String createdByName, Instant createdAt) {}

    public record RiskSettingsDto(
            int reviewAt, int warningAt, int investigationAt, int windowDays, int misconductAt, int misconductWindowDays,
            boolean suspensionReviewOnCritical, boolean customised, String updatedByName, Instant updatedAt) {}

    public record SaveRiskSettingsRequest(
            int reviewAt, int warningAt, int investigationAt, int windowDays, int misconductAt, int misconductWindowDays,
            boolean suspensionReviewOnCritical) {}

    public record ScoreComponent(String key, String label, int weight, boolean hasData, int percent, double points, String detail) {}

    public record RecommendationItem(String code, String level, String title, String reason) {}

    /** The window the figures cover and the counts the recommendations were based on, so the page can show its working. */
    public record Basis(int days, int complaintsCounted, int substantiatedMisconduct, int criticalIncidents, int openUrgentComplaints) {}

    public record PerformanceResponse(
            Integer score, String band, int coverage, List<ScoreComponent> components,
            List<RecommendationItem> recommendations, Basis basis, RiskSettingsDto settings,
            Double ratingAverage, int ratingCount, List<RatingItem> ratings) {}
}
