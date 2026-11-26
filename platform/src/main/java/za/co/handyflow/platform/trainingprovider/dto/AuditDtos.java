package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record TemplateRequest(@NotBlank String name, @NotBlank String scopeType, @NotBlank String items) {
    }

    public record VersionRequest(@NotBlank String items, String changeNote) {
    }

    public record RetireRequest(@NotBlank String reason) {
    }

    public record TemplateView(UUID id, String templateNo, String name, String scopeType, String status, String retiredReason, int latestVersionNo, UUID latestVersionId,
                               List<String> items, int versionCount) {
    }

    public record AuditRequest(@NotNull UUID templateId, @NotBlank String title, @NotBlank String scopeType, UUID siteId, UUID cohortId, UUID programmeId, @NotNull LocalDate plannedOn) {
    }

    public record AnswerRequest(@NotBlank String outcome, String note) {
    }

    public record ActionRequest(UUID itemId, @NotBlank String description, @NotBlank String ownerName, @NotNull LocalDate dueOn) {
    }

    public record DoneRequest(@NotBlank String note) {
    }

    public record VerifyRequest(boolean confirmAlone, String aloneReason) {
    }

    public record CompleteRequest(@NotBlank String summary, boolean confirmAlone, String aloneReason) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record AuditSummary(UUID id, String auditNo, String title, String scopeType, String targetName, LocalDate plannedOn, String status, int items, int answered, int issues,
                               int openActions) {
    }

    public record ItemView(UUID id, int position, String question, String outcome, String note, Instant answeredAt) {
    }

    public record ActionView(UUID id, UUID itemId, String description, String ownerName, LocalDate dueOn, String status, boolean overdue, LocalDate doneOn, String doneNote,
                             boolean verified, boolean verifiedAlone, String verifiedAloneReason, String cancelledReason) {
    }

    public record AuditDetail(UUID id, String auditNo, String title, String scopeType, String targetName, UUID leadUserId, LocalDate plannedOn, String status, LocalDate startedOn,
                              LocalDate completedOn, boolean completedAlone, String completedAloneReason, String summary, String cancelledReason, String templateName, int templateVersionNo,
                              String note, List<ItemView> items, List<ActionView> actions) {
    }
}
