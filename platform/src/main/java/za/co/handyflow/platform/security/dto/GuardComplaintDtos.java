// security/dto/GuardComplaintDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for guard complaints. */
public final class GuardComplaintDtos {

    private GuardComplaintDtos() {}

    public record SaveComplaintRequest(
            @NotNull UUID guardId, UUID siteId, @NotNull LocalDate occurredOn,
            @NotBlank String category, @NotBlank String severity, @NotBlank String description,
            @NotBlank String complainantType, String complainantName, String complainantContact, String witnesses) {}

    public record StartInvestigationRequest(String investigator) {}

    public record FindingRequest(@NotBlank String finding, String note) {}

    public record ActionRequest(@NotBlank String action, String note) {}

    public record CloseRequest(String resolutionNote) {}

    public record WithdrawRequest(@NotBlank String reason) {}

    /** A row in a list: enough to scan, nothing sensitive beyond the headline. */
    public record ComplaintSummary(
            UUID id, String complaintNumber, UUID guardId, String guardName, UUID siteId, String siteName,
            LocalDate occurredOn, String category, String severity, String status, String finding, String action,
            boolean open, boolean urgent, Instant createdAt) {}

    public record EventItem(UUID id, String eventType, String toStatus, String note, String byName, Instant at) {}

    public record ComplaintDetail(
            ComplaintSummary summary, String description, String complainantType, String complainantName,
            String complainantContact, String witnesses, String investigatorName,
            String findingNote, String findingByName, Instant findingAt,
            String actionNote, String actionByName, Instant actionAt,
            String resolutionNote, String closedByName, Instant closedAt, String withdrawnReason,
            String createdByName, boolean editable, List<String> allowedSteps,
            List<EventItem> events, List<GuardOverviewResponse.EvidenceItem> evidence) {}

    public record ComplaintCounts(int open, int last90Days, int substantiatedLast90Days) {}
}
