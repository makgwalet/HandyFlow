package za.co.handyflow.platform.legalcompliance.dto;

import za.co.handyflow.platform.legalcompliance.domain.model.ObligationCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RecurrenceInterval;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RegulatoryObligationResponse(
        UUID id,
        String title,
        ObligationCategory category,
        String regulationReference,
        String description,
        UUID responsibleUserId,
        String responsibleUserName,
        LocalDate reviewDate,
        RecurrenceInterval recurrence,
        ObligationStatus status,
        UUID linkedContractId,
        String notes,
        Instant lastReviewedAt,
        String lastReviewedByName,
        Instant createdAt,
        Instant updatedAt
) {
    public static RegulatoryObligationResponse of(RegulatoryObligation o) {
        return new RegulatoryObligationResponse(
                o.getId(), o.getTitle(), o.getCategory(), o.getRegulationReference(), o.getDescription(),
                o.getResponsibleUserId(), o.getResponsibleUserName(), o.getReviewDate(), o.getRecurrence(),
                o.getStatus(), o.getLinkedContractId(), o.getNotes(), o.getLastReviewedAt(),
                o.getLastReviewedByName(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
