package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class SubmissionDtos {

    private SubmissionDtos() {}

    public record SubmitRequest(@NotNull SubmissionKind kind, @NotNull SubmissionArea area, SubmissionPriority priority, @NotBlank @Size(max = 200) String title,
                                @NotBlank @Size(max = 4000) String body, LocalDate respondBy) {}

    /** note is optional when accepting and required (10+ characters) when declining or completing. */
    public record RespondRequest(@Size(max = 2000) String note) {}

    public record ScopesRequest(@NotEmpty Set<AccessScope> scopes) {}

    /** The same view serves both sides: the client sees every submission in its inbox; the provider sees only the ones it sent. The reply is shown to the provider. */
    public record SubmissionView(UUID id, String reference, UUID relationshipId, String providerName, SubmissionKind kind, SubmissionArea area, SubmissionPriority priority, String title, String body,
                                 LocalDate respondBy, String submittedBy, SubmissionStatus status, String response, String respondedBy, Instant respondedAt, Instant sentAt, boolean overdue) {}

    public record Inbox(long open, java.util.List<SubmissionView> items) {}
}
