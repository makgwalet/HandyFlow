package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProviderWorkDtos {

    private ProviderWorkDtos() {}

    public record AssignmentRequest(@NotNull UUID providerId, @NotBlank @Size(max = 200) String title, @Size(max = 4000) String description, @Size(max = 60) String serviceCode,
                                    LocalDate dueDate, Boolean accreditationRequired, @Size(max = 4000) String waiverNote) {}

    public record DeclarationRequest(boolean conflict, @NotBlank @Size(max = 4000) String note) {}

    public record NoteRequest(@NotBlank @Size(max = 4000) String note) {}

    public record DeliverableRequest(@NotBlank @Size(max = 200) String title, LocalDate dueDate) {}

    public record ReviewRequest(boolean accepted, @NotBlank @Size(max = 4000) String note) {}

    public record CompleteRequest(int rating, @NotBlank @Size(max = 4000) String note) {}

    public record DeliverableView(UUID id, String title, LocalDate dueDate, String status, UUID receivedBy, Instant receivedAt, UUID reviewedBy, Instant reviewedAt, String reviewNote) {}

    public record AssignmentView(UUID id, UUID providerId, String providerName, UUID clientId, String clientName, String title, String description, String serviceCode, LocalDate dueDate,
                                 String status, boolean accreditationRequired, String waiverNote, String conflictStatus, String conflictNote, UUID conflictBy, boolean conflictApproved,
                                 UUID conflictApprovedBy, String conflictApprovalNote, String problemStarting, LocalDate completedOn, Integer rating, Boolean onTime, String completionNote,
                                 String cancelNote, Instant createdAt) {}

    public record AssignmentDetail(AssignmentView assignment, List<DeliverableView> deliverables) {}

    /** Counts and an average over finished work. Not a score for the provider, and nothing is shown when there is no finished work. */
    public record PerformanceView(UUID providerId, int completed, int onTime, Double averageRating, int cancelled, int open) {}
}
