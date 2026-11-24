package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CaseDtos {

    private CaseDtos() {}

    /** Open a case. With an assessmentId and obligationId the case is taken from that assessed item. */
    public record OpenRequest(@Size(max = 200) String title, @Size(max = 4000) String description, @Size(max = 10) String severity,
                              UUID ownerId, LocalDate dueDate, UUID assessmentId, UUID obligationId) {}

    public record TransitionRequest(@NotBlank @Size(max = 20) String action, @Size(max = 4000) String note) {}

    public record OwnerRequest(UUID ownerId) {}

    public record DueRequest(LocalDate dueDate) {}

    public record StepRequest(@NotBlank @Size(max = 500) String description, LocalDate dueDate) {}

    public record StepCancelRequest(@Size(max = 2000) String reason) {}

    public record StepView(UUID id, String description, String status, LocalDate dueDate, UUID doneBy, Instant doneAt, String cancelReason) {}

    public record EventView(String event, String fromStatus, String toStatus, String note, UUID actor, Instant occurredAt) {}

    public record CaseSummary(UUID id, String title, String severity, String status, UUID ownerId, LocalDate dueDate, UUID assessmentId,
                              UUID obligationId, String obligationCode, int openSteps, int totalSteps, Instant createdAt) {}

    public record CaseDetail(CaseSummary summary, String description, String resolutionNote, UUID resolvedBy, Instant resolvedAt, UUID closedBy,
                             Instant closedAt, String closeExceptionReason, String cancelReason, List<StepView> steps, List<EventView> events) {}
}
