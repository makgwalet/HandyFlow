package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ActionPlanDtos {
    private ActionPlanDtos() {}

    public record CreateRequest(@NotBlank String description, UUID owner, @NotNull LocalDate dueDate) {}
    public record NoteRequest(String note) {}

    public record Response(UUID id, UUID findingId, String description, UUID owner, String ownerName, LocalDate dueDate, String status,
                           boolean overdue, String completionNote, Instant completedAt, String validationNote, Instant validatedAt,
                           int timesReturned, String cancelReason, Instant createdAt) {}
}
