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
        Readiness readiness,
        List<GuardCompetencyResponse> competencies,
        List<ShiftItem> shifts,
        List<IncidentItem> incidents,
        Counts counts,
        List<GuardComplaintDtos.ComplaintSummary> complaints,
        GuardComplaintDtos.ComplaintCounts complaintCounts
) {
    public record ScreeningItem(
            UUID id, String screeningType, String reason, String result,
            String conductedBy, LocalDate conductedAt, LocalDate nextDueAt,
            String reportRef, Instant createdAt,
            String provider, LocalDate requestedAt,
            String decision, String decisionNote, String decidedByName, Instant decidedAt,
            List<EvidenceItem> evidence) {}

    public record EvidenceItem(UUID id, String fileName, String label, long sizeBytes, String uploadedByName, Instant createdAt) {}

    /** Deployment readiness: percent of required checks met, a ready flag, every row, and the reasons it is not ready. */
    public record Readiness(int percent, boolean ready, List<ReadinessItem> items, List<String> reasons) {}

    public record ReadinessItem(String key, String label, boolean required, String state, String detail,
                                LocalDate validUntil, int evidenceCount, boolean met,
                                UUID screeningId, UUID competencyId) {}

    public record ShiftItem(
            UUID id, UUID siteId, String siteName, Instant startAt, Instant endAt,
            String status, Instant actualStartAt, Integer minutesLate) {}

    public record IncidentItem(
            UUID id, UUID siteId, String siteName, String title, String severity,
            String status, Instant reportedAt) {}

    /** Shift and incident counts over the look-back window, for the header tiles. */
    public record Counts(int shiftsLast90Days, int completedLast90Days,
                         int incidentsLast180Days, int openIncidents) {}
}
