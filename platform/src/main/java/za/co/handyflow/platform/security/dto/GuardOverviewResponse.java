// security/dto/GuardOverviewResponse.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the Guard 360 page needs in one call: the guard, the guard's
 * compliance documents, the full screening history, recent and upcoming
 * shifts and recent incidents. Read-only; each list is capped so one very
 * busy guard cannot produce a huge payload.
 */
public record GuardOverviewResponse(
        GuardResponse guard,
        String screeningGate,
        List<GuardDocumentResponse> documents,
        List<ScreeningItem> screening,
        List<ShiftItem> shifts,
        List<IncidentItem> incidents,
        Counts counts
) {
    public record ScreeningItem(
            UUID id, String screeningType, String reason, String result,
            String conductedBy, LocalDate conductedAt, LocalDate nextDueAt,
            String reportRef, Instant createdAt) {}

    public record ShiftItem(
            UUID id, UUID siteId, String siteName, Instant startAt, Instant endAt,
            String status) {}

    public record IncidentItem(
            UUID id, UUID siteId, String siteName, String title, String severity,
            String status, Instant reportedAt) {}

    /** Shift and incident counts over the look-back window, for the header tiles. */
    public record Counts(int shiftsLast90Days, int completedLast90Days,
                         int incidentsLast180Days, int openIncidents) {}
}
