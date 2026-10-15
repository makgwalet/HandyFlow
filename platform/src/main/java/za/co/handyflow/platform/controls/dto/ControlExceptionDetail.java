package za.co.handyflow.platform.controls.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The full management picture of one exception. {@link ControlExceptionResponse} stays as the narrow shape other modules use. */
public record ControlExceptionDetail(
        UUID id, String sourceModule, String controlType, String relatedEntityType, UUID relatedEntityId,
        String severity, String description, String status, Instant detectedAt,
        String riskRating, String riskRationale, String ratedByName, Instant ratedAt,
        UUID ownerId, String ownerName, LocalDate targetDate, boolean overdue,
        String responseText, String responseByName, Instant responseAt,
        String resolvedByName, Instant resolvedAt, String resolutionNotes,
        LocalDate followUpDate, boolean followUpDue, Instant followUpAt, String followUpByName,
        String followUpOutcome, String followUpNote, int timesReopened, int evidenceCount
) {}
