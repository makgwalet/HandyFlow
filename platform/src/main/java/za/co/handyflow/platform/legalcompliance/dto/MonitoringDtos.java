package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.FetchStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchItemStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Regulatory monitoring. Suggestions are advisory and never stored. */
public final class MonitoringDtos {

    private MonitoringDtos() {}

    public record SourceRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String authority, @NotBlank @Size(max = 500) String feedUrl) {}

    /** active may be null to leave it unchanged. The address cannot be changed. */
    public record SourceUpdateRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String authority, Boolean active) {}

    public record SourceResponse(UUID id, String name, String authority, String feedUrl, boolean active, Instant lastFetchedAt, FetchStatus lastStatus, String lastError,
                                 int lastNewItems, int consecutiveFailures, boolean needsAttention) {}

    public record ItemResponse(UUID id, UUID sourceId, String sourceName, String title, String link, String summary, LocalDate publishedOn, Instant fetchedAt,
                               WatchItemStatus status, String decisionNote, String decidedByName, Instant decidedAt) {}

    public record DecisionRequest(@NotNull WatchItemStatus outcome, @Size(max = 2000) String note) {}

    public record FetchOutcome(int fetched, int added, boolean failed, String error, boolean alertDue) {}

    public record AssistRequest(@NotBlank @Size(max = 20000) String text) {}

    public record SuggestionResponse(String type, UUID id, String reference, String label, String strength, double score, boolean referenceMentioned, List<String> matchedTerms) {}

    public record MonitoringSummary(long activeSources, long sourcesFailing, long newItems) {}
}
