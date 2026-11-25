package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TimeDtos {

    private TimeDtos() {}

    public record RecordRequest(@NotNull UUID clientId, @NotNull LocalDate workDate, int minutes, @Size(max = 60) String serviceCode, String kind, Boolean billable, @Size(max = 1000) String note) {}

    public record VoidRequest(@NotBlank @Size(max = 4000) String reason) {}

    public record EntryView(UUID id, UUID clientId, String clientName, UUID userId, LocalDate workDate, int minutes, String serviceCode, String kind, boolean billable, String note,
                            String status, String voidReason, Instant createdAt) {}

    public record Bucket(String key, String label, int minutes, int billableMinutes, int reworkMinutes, int unplannedMinutes) {}

    /** Hours only. No cost and no money appear here. */
    public record Summary(LocalDate from, LocalDate to, int minutes, int billableMinutes, int reworkMinutes, int unplannedMinutes, List<Bucket> byClient, List<Bucket> byDay) {}
}
