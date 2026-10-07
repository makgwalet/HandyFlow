// security/dto/IncidentCaseDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shapes for working an incident: assignment, escalation, notes, reopening, and the case view with its timeline. */
public final class IncidentCaseDtos {

    private IncidentCaseDtos() {}

    public record AssignRequest(@NotBlank String assigneeName) {}

    /** severity is optional: when absent the incident moves up one level. */
    public record EscalateRequest(String severity, @NotBlank String reason) {}

    public record NoteRequest(@NotBlank String note) {}

    public record ReopenRequest(@NotBlank String reason) {}

    /** Optional body on resolve: how it was dealt with. */
    public record ResolveRequest(String note) {}

    public record EventItem(UUID id, String eventType, String toStatus, String note, String byName, Instant at) {}

    public record CaseDetail(
            IncidentResponse incident, String assigneeName, Instant assignedAt,
            List<String> allowedActions, List<EventItem> events, List<GuardOverviewResponse.EvidenceItem> evidence) {}
}
