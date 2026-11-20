package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for matter documents. */
public final class DocumentDtos {

    private DocumentDtos() { }

    public record DocumentSummary(UUID id, UUID matterId, String matterLabel, UUID clientId, String title, String category, String status, int currentVersion,
                                  boolean legalHold, UUID checkedOutBy, String checkedOutByName, Instant checkedOutAt, Instant updatedAt) { }

    public record VersionDto(int versionNo, String fileName, String contentType, long sizeBytes, String sha256, String changeNote, String uploadedByName, Instant uploadedAt) { }

    public record EventDto(String eventType, Integer versionNo, String actorName, String detail, Instant occurredAt) { }

    public record DocumentDetail(DocumentSummary document, String holdReason, String holdSetByName, Instant holdSetAt, String archivedReason, String createdByName, Instant createdAt,
                                 List<VersionDto> versions, List<EventDto> events) { }

    public record UpdateDocumentRequest(@NotBlank String title, @NotBlank String category) { }

    public record ReasonRequest(@NotBlank String reason) { }

    public record VerifyResponse(int versionNo, boolean matches, String recordedSha256) { }
}
