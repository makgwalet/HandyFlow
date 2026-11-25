package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContactDtos {

    private ContactDtos() {}

    // ---- requests in ----
    public record RaiseRequest(UUID clientId, @NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 4000) String body) {}

    public record BodyRequest(@NotBlank @Size(max = 4000) String body) {}

    public record NoteRequest(@NotBlank @Size(max = 4000) String note) {}

    public record DocumentRequestInput(@NotBlank @Size(max = 200) String title, @Size(max = 4000) String description, @Size(max = 100) String documentType, LocalDate dueDate) {}

    // ---- the practice's view ----
    public record StaffMessage(String authorType, UUID authorId, String authorName, String body, Instant createdAt) {}

    public record StaffQuery(UUID id, UUID clientId, String clientName, String subject, String status, String raisedByName, Instant createdAt, Instant lastMessageAt, int messageCount) {}

    public record StaffQueryDetail(StaffQuery query, List<StaffMessage> messages) {}

    public record StaffRequest(UUID id, UUID clientId, String clientName, String title, String description, String documentType, LocalDate dueDate, String status,
                               Instant requestedAt, Instant closedAt, String closeNote, boolean late) {}

    // ---- what a client sees: no staff names, no internal notes ----
    public record PortalMessage(boolean fromClient, String author, String body, Instant createdAt) {}

    public record PortalQuery(UUID id, String subject, String status, Instant createdAt, Instant lastMessageAt, int messageCount) {}

    public record PortalQueryDetail(PortalQuery query, List<PortalMessage> messages) {}

    public record PortalRequest(UUID id, String title, String description, String documentType, LocalDate dueDate, String status, Instant requestedAt, boolean late) {}
}
