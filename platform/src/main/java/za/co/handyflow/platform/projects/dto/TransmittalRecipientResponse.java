package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalRecipient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff view of one recipient. Never carries the link or any secret. */
public record TransmittalRecipientResponse(
        UUID id, String name, String organisation, String email,
        boolean canDownload, boolean canAcknowledge,
        String status, Instant expiresAt, Instant revokedAt, String revokedByName, String revokeReason,
        Instant emailSentAt, Instant verifiedAt, Instant lastAccessAt, Instant acknowledgedAt,
        List<UUID> documentIds
) {
    /** ACTIVE, EXPIRED or REVOKED. documentIds empty means every document on the transmittal. */
    public static TransmittalRecipientResponse of(ProjectTransmittalRecipient r, List<UUID> scopedItemIds, Instant now) {
        String status = r.isRevoked() ? "REVOKED" : r.getExpiresAt().isAfter(now) ? "ACTIVE" : "EXPIRED";
        return new TransmittalRecipientResponse(r.getId(), r.getName(), r.getOrganisation(), r.getEmail(),
                r.isCanDownload(), r.isCanAcknowledge(), status, r.getExpiresAt(), r.getRevokedAt(),
                r.getRevokedByName(), r.getRevokeReason(), r.getEmailSentAt(), r.getVerifiedAt(),
                r.getLastAccessAt(), r.getAcknowledgedAt(), scopedItemIds);
    }
}
