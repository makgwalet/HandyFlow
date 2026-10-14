package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.application.internal.DisputeRules;
import za.co.handyflow.platform.debtcollection.domain.model.Dispute;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A dispute on a collection case (finance F16). */
public record DisputeResponse(
        UUID id, UUID caseId, String caseNumber, String debtorName, UUID invoiceId, String reasonType, String description,
        BigDecimal amountDisputed, String status, LocalDate raisedOn, LocalDate responseDue, boolean overdue,
        UUID ownerUserId, String ownerUserName, String resolution, BigDecimal amountConceded, String resolutionNote,
        String resolvedByName, Instant resolvedAt, String creditNoteRef
) {
    public static DisputeResponse of(Dispute d, String caseNumber, String debtorName, LocalDate today) {
        return new DisputeResponse(d.getId(), d.getCaseId(), caseNumber, debtorName, d.getInvoiceId(), d.getReasonType(),
                d.getDescription(), d.getAmountDisputed(), d.getStatus(), d.getRaisedOn(), d.getResponseDue(),
                DisputeRules.overdue(d.getStatus(), d.getResponseDue(), today), d.getOwnerUserId(), d.getOwnerUserName(),
                d.getResolution(), d.getAmountConceded(), d.getResolutionNote(), d.getResolvedByName(), d.getResolvedAt(),
                d.getCreditNoteRef());
    }
}
