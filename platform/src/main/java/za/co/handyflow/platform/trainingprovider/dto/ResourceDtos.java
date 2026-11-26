package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ResourceDtos {
    private ResourceDtos() {
    }

    public record ResourceRequest(@NotNull UUID siteId, UUID venueId, @NotBlank String name, @NotBlank String resourceType, String identifier, Integer quantity,
                                  boolean shareable, String notes) {
    }

    public record StatusRequest(@NotBlank String toStatus, @NotBlank String reason, @NotNull LocalDate changedOn) {
    }

    public record MoveRequest(@NotNull UUID siteId, UUID venueId, @NotBlank String reason, @NotNull LocalDate movedOn) {
    }

    public record EventView(UUID id, String eventType, String fromStatus, String toStatus, String fromSiteName, String toSiteName, String note, LocalDate occurredOn, Instant createdAt) {
    }

    public record ResourceView(UUID id, String resourceNo, UUID siteId, String siteName, UUID venueId, String venueName, String name, String resourceType, String identifier,
                               int quantity, boolean shareable, String notes, String status, String statusReason, LocalDate statusChangedOn, List<String> nextStatuses) {
    }

    public record ResourceDetail(ResourceView resource, List<EventView> history) {
    }
}
