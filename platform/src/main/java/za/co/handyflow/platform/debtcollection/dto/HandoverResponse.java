package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CreditAgreementType;
import za.co.handyflow.platform.debtcollection.domain.model.HandoverStatus;
import za.co.handyflow.platform.debtcollection.domain.model.LegalHandover;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** The package itself is not inlined — fetch it from /legal-handovers/{id}/package; packageHash identifies it. */
public record HandoverResponse(
        UUID id,
        UUID caseId,
        HandoverStatus status,
        CreditAgreementType agreementType,
        String creditorAuthorityNote,
        Set<UUID> evidenceIds,
        boolean prescriptionReviewed,
        String prescriptionReviewNote,
        Instant prescriptionReviewedAt,
        String notes,
        int packageVersion,
        String packageHash,
        Instant packageBuiltAt,
        String preparedByName,
        Instant submittedAt,
        String approvedByName,
        Instant approvedAt,
        String lastReturnReason,
        UUID legalMatterId,
        String legalMatterNumber,
        Instant transferredAt,
        String legalStatus,
        Instant legalStatusUpdatedAt,
        String cancelReason,
        Instant createdAt
) {
    public static HandoverResponse of(LegalHandover h) {
        return new HandoverResponse(h.getId(), h.getCaseId(), h.getStatus(), h.getAgreementType(),
                h.getCreditorAuthorityNote(), h.getEvidenceIds(), h.isPrescriptionReviewed(), h.getPrescriptionReviewNote(),
                h.getPrescriptionReviewedAt(), h.getNotes(), h.getPackageVersion(), h.getPackageHash(), h.getPackageBuiltAt(),
                h.getPreparedByName(), h.getSubmittedAt(), h.getApprovedByName(), h.getApprovedAt(), h.getLastReturnReason(),
                h.getLegalMatterId(), h.getLegalMatterNumber(), h.getTransferredAt(), h.getLegalStatus(),
                h.getLegalStatusUpdatedAt(), h.getCancelReason(), h.getCreatedAt());
    }
}
