package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReminderDtos {

    private ReminderDtos() {}

    public record RecipientRequest(@NotNull UUID userId, boolean adminItems) {}

    public record SettingsRequest(boolean enabled, @NotBlank @Size(max = 6) String minSeverity, int maxItems, List<RecipientRequest> recipients) {}

    public record RecipientView(UUID userId, String name, String email, boolean adminItems) {}

    public record SettingsView(boolean enabled, String minSeverity, int maxItems, List<RecipientView> recipients, Instant updatedAt) {}

    public record PreviewView(UUID userId, String name, boolean adminItems, int itemCount, String subject, String body) {}

    public record RunResult(int handedOver, int nothingDue, int noAddress, int failed, int alreadyDone) {}

    public record LogView(LocalDate runDate, UUID userId, String name, int itemCount, String outcome, String outcomeText, String detail, Instant at) {}
}
