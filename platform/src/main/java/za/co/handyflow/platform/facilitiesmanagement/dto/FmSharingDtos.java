package za.co.handyflow.platform.facilitiesmanagement.dto;

import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Secure sharing of a completed work order's files: requests and what the FM sharing screen reads. */
public final class FmSharingDtos {
    private FmSharingDtos() {}

    /**
     * {@code evidenceIds}: the files to share, required for the first recipient (they fix what this work order shares) and refused
     * after that. {@code attach}: also email the files (only when attachments are switched on for the platform).
     */
    public record RecipientRequest(List<UUID> evidenceIds, String name, String organisation, String email, Boolean canDownload,
                                   Boolean canAcknowledge, Integer expiryDays, Boolean attach) {}

    public record ReissueRequest(Integer expiryDays) {}

    public record RevokeRequest(String reason) {}

    public record WorkOrderRow(UUID id, String workOrderNumber, String description, String context, Instant completedAt,
                               int files, boolean shared, boolean filesDisposed) {}

    /** {@code shared}: this file is part of the share. */
    public record FileRow(UUID id, String fileName, String contentType, long sizeBytes, String sha256, String evidenceType,
                          String uploadedByName, Instant createdAt, boolean shared) {}

    /** {@code shareable}: completed, files not disposed of, client active. */
    public record SharingView(UUID workOrderId, String workOrderNumber, String description, String context, String status,
                              Instant completedAt, boolean shareable, String notShareableReason, boolean filesDisposed,
                              List<FileRow> files, UUID shareId, List<RecipientView> recipients) {}
}
