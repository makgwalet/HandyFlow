package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class SmsReminderDtos {

    private SmsReminderDtos() {}

    public record SettingsRequest(boolean enabled) {}

    public record SettingsView(boolean enabled, Instant updatedAt) {}

    public record ConsentRequest(@NotBlank @Size(max = 200) String contactName, @NotBlank @Size(max = 30) String phone, @NotBlank @Size(max = 1000) String basis) {}

    public record WithdrawRequest(@NotBlank @Size(max = 1000) String note) {}

    public record ConsentView(UUID id, UUID clientId, String contactName, String phone, String basis, UUID givenBy, Instant givenAt,
                              boolean active, UUID withdrawnBy, Instant withdrawnAt, String withdrawnNote) {}

    public record LogView(UUID id, UUID clientId, String clientName, UUID deadlineId, String kind, LocalDate runDate, String outcome, String outcomeText, String detail, Instant createdAt) {}

    public record RunResult(int handedOver, int failed, int alreadySent, int noConsent) {}
}
