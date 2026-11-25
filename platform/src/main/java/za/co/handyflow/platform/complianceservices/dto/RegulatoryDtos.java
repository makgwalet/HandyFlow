package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RegulatoryDtos {

    private RegulatoryDtos() {}

    public record SourceRequest(@NotBlank @Size(max = 200) String name, @Size(max = 200) String authority, @Size(max = 500) String url, @Size(max = 12) String kind) {}

    public record SourceView(UUID id, String name, String authority, String url, String kind, boolean active) {}

    public record ActiveRequest(boolean active) {}

    public record ChangeRequest(@NotNull UUID sourceId, @NotBlank @Size(max = 250) String title, @Size(max = 4000) String summary, @NotBlank @Size(max = 10) String changeType,
                                LocalDate publicationDate, LocalDate effectiveDate, @Size(max = 500) String referenceUrl, List<UUID> obligationIds) {}

    public record ReviewRequest(@Size(max = 2000) String note, @Size(max = 1000) String exceptionReason) {}

    public record ImpactActionRequest(@NotBlank @Size(max = 12) String action, @Size(max = 2000) String note) {}

    public record ObligationRef(UUID id, String code, String name) {}

    public record ChangeSummary(UUID id, UUID sourceId, String sourceName, String title, String changeType, String status, String origin, LocalDate publicationDate,
                                LocalDate effectiveDate, Instant raisedAt, int linkedObligations, int impactsOpen, int impactsTotal, List<String> actions) {}

    /** A client who would be affected, shown before a change is confirmed. */
    public record AffectedClient(UUID clientId, String clientName, String obligationCode, String state) {}

    public record ImpactView(UUID id, UUID changeId, String changeTitle, UUID clientId, String clientName, String obligationCode, String status, LocalDate dueDate,
                             String note, Instant handledAt, Instant noticeGivenAt, List<String> actions) {}

    public record ChangeDetail(ChangeSummary change, String summary, String referenceUrl, List<ObligationRef> obligations, Instant reviewedAt, String reviewNote,
                               String reviewExceptionReason, List<AffectedClient> affected, List<ImpactView> impacts) {}

    public record Generated(int added, int total) {}
}
