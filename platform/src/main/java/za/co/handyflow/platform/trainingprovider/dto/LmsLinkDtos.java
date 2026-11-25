package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class LmsLinkDtos {
    private LmsLinkDtos() {
    }

    public record LinkRequest(@NotNull UUID programmeVersionId, @NotBlank String systemName, @NotBlank String launchUrl, String notes) {
    }

    public record LinkUpdate(@NotBlank String systemName, @NotBlank String launchUrl, String notes) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record LinkView(UUID id, UUID programmeVersionId, Integer versionNo, UUID programmeId, String programmeTitle, String systemName, String launchUrl, String notes,
                           String status, String endedReason, Instant createdAt) {
    }
}
