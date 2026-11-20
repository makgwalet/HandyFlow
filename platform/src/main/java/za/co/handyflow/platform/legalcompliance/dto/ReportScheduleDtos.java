package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.application.internal.ScheduleRules.Frequency;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Scheduled report delivery. The report and its audience are fixed when the schedule is created. */
public final class ReportScheduleDtos {

    private ReportScheduleDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 60) String reportKey, @NotNull Frequency frequency, int runDay, @NotNull List<UUID> recipientIds) {}

    public record UpdateRequest(@NotBlank @Size(max = 255) String name, @NotNull Frequency frequency, int runDay, @NotNull List<UUID> recipientIds, Boolean active) {}

    public record Recipient(UUID userId, String name) {}

    public record ScheduleResponse(UUID id, String name, String reportKey, String reportTitle, Frequency frequency, int runDay, String audience, List<Recipient> recipients, boolean active,
                                   LocalDate nextRunOn, LocalDate lastRunOn, String lastStatus, Integer lastRowCount, String lastError, String createdByName) {}
}
