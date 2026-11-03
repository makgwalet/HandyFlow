package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff view of one recipient. Never carries the link or any secret. */
public record TransmittalRecipientResponse(
        UUID id, String name, String organisation, String email,
        boolean canDownload, boolean canAcknowledge,
        String status, Instant expiresAt, Instant revokedAt, String revokedByName, String revokeReason,
        Instant emailSentAt, Instant verifiedAt, Instant lastAccessAt, Instant acknowledgedAt,
        List<UUID> documentIds
) {
    /**
     * From the secure access engine's view. ACTIVE, EXPIRED or REVOKED. documentIds empty means every document
     * on the transmittal; itemToDocument turns the engine's share-item ids back into project document ids.
     */
    public static TransmittalRecipientResponse of(RecipientView v, Map<UUID, UUID> itemToDocument) {
        List<UUID> docs = v.itemIds() == null ? List.of()
                : v.itemIds().stream().map(itemToDocument::get).filter(java.util.Objects::nonNull).toList();
        return new TransmittalRecipientResponse(v.id(), v.name(), v.organisation(), v.email(), v.canDownload(),
                v.canAcknowledge(), v.status(), v.expiresAt(), v.revokedAt(), v.revokedByName(), v.revokeReason(),
                v.emailSentAt(), v.verifiedAt(), v.lastAccessAt(), v.acknowledgedAt(), docs);
    }
}
