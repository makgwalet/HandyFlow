// security/dto/GuardReviewDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shapes for supervisor reviews of guards. */
public final class GuardReviewDtos {
    private GuardReviewDtos() {}

    public record SaveReviewRequest(
            UUID siteId, @NotNull LocalDate periodFrom, @NotNull LocalDate periodTo, @NotNull LocalDate reviewDate,
            @NotBlank String overall,
            int punctuality, int professionalism, int appearance, int communication, int alertness, int incidentHandling,
            String strengths, String improvements, String trainingNeeds, String actionsAgreed, LocalDate followUpDate) {}

    public record ReviewItem(
            UUID id, UUID siteId, String siteName, LocalDate reviewDate, LocalDate periodFrom, LocalDate periodTo, String reviewerName,
            String overall, int punctuality, int professionalism, int appearance, int communication, int alertness, int incidentHandling,
            double average, String strengths, String improvements, String trainingNeeds, String actionsAgreed, LocalDate followUpDate,
            Instant createdAt) {}

    /** `dueState`: NONE (never reviewed), OVERDUE (last review more than the interval ago), FOLLOW_UP_DUE, or OK. */
    public record ReviewList(List<ReviewItem> reviews, LocalDate lastReviewOn, Integer daysSinceLast, String dueState, int intervalDays) {}
}
