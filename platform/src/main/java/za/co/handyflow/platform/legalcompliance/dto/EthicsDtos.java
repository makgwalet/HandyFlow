package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Conflict-of-interest declarations and the gifts and hospitality register. */
public final class EthicsDtos {

    private EthicsDtos() {}

    public record DeclarationRequest(@NotNull DeclarationType declarationType, @NotBlank @Size(max = 255) String counterparty, @NotBlank String description, BigDecimal estimatedValue, int reviewMonths) {}

    public record DecisionRequest(@NotNull DeclarationStatus outcome, @NotBlank String note, String mitigation) {}

    public record GiftRequest(@NotNull GiftDirection direction, @NotBlank @Size(max = 255) String counterparty, @NotBlank String description, @Size(max = 255) String occasion,
                              @NotNull LocalDate entryDate, @NotNull BigDecimal valueZar) {}

    public record GiftDecisionRequest(String note) {}

    public record DeclarationResponse(UUID id, String reference, UUID declarantId, String declarantName, DeclarationType declarationType, String counterparty, String description,
                                      BigDecimal estimatedValue, LocalDate declaredOn, DeclarationStatus status, String reviewerName, LocalDate reviewedOn, String decisionNote, String mitigation,
                                      int reviewMonths, LocalDate nextReviewDue, boolean reviewOverdue, boolean mine) {}

    public record GiftResponse(UUID id, String reference, GiftDirection direction, UUID declarantId, String declarantName, String counterparty, String description, String occasion,
                               LocalDate entryDate, BigDecimal valueZar, BigDecimal thresholdZar, GiftStatus status, String decidedByName, LocalDate decidedOn, String decisionNote, boolean mine) {}

    public record EthicsSummary(long awaitingReview, long inForce, long reviewOverdue, long giftsPending, long notPermitted) {}
}
