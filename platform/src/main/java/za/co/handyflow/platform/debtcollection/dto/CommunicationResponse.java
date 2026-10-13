package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationDirection;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.domain.model.LetterDispatchMethod;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** blockedReason is set when status is BLOCKED; suggestedAlternatives then lists the other enabled channels. */
public record CommunicationResponse(
        UUID id,
        UUID caseId,
        CommunicationChannel channel,
        CommunicationDirection direction,
        CommunicationPurpose purpose,
        CommunicationStatus status,
        String recipient,
        String subject,
        String body,
        String templateCode,
        Integer templateVersion,
        String providerMessageId,
        String statusDetail,
        String blockedReason,
        Set<CommunicationChannel> suggestedAlternatives,
        String processingBasis,
        LetterDispatchMethod dispatchMethod,
        String trackingReference,
        UUID evidenceId,
        Instant sentAt,
        Instant deliveredAt,
        Instant receivedAt,
        String createdByUserName,
        Instant createdAt
) {
    public static CommunicationResponse of(Communication c, Set<CommunicationChannel> alternatives) {
        return new CommunicationResponse(c.getId(), c.getCaseId(), c.getChannel(), c.getDirection(), c.getPurpose(),
                c.getStatus(), c.getRecipient(), c.getSubject(), c.getBody(), c.getTemplateCode(), c.getTemplateVersion(),
                c.getProviderMessageId(), c.getStatusDetail(),
                c.getStatus() == CommunicationStatus.BLOCKED ? c.getStatusDetail() : null,
                alternatives != null ? alternatives : Set.of(), c.getProcessingBasis(), c.getDispatchMethod(),
                c.getTrackingReference(), c.getEvidenceId(), c.getSentAt(), c.getDeliveredAt(), c.getReceivedAt(),
                c.getCreatedByUserName(), c.getCreatedAt());
    }

    public static CommunicationResponse of(Communication c) {
        return of(c, Set.of());
    }
}
