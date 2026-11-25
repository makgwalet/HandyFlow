package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ModerationDtos {
    private ModerationDtos() {
    }

    public record RecordModeration(@NotNull UUID moderatorId, @NotNull LocalDate moderatedOn, @NotBlank String outcome, @NotBlank String basis) {
    }

    public record ResolveRequest(@NotBlank String note) {
    }

    public record ModerationView(UUID id, String moderationNo, UUID attemptId, String attemptNo, String instrumentTitle, String learnerName, String assessorName,
                                 String decision, UUID moderatorId, String moderatorName, LocalDate moderatedOn, String outcome, String basis,
                                 String resolutionNote, Instant resolvedAt) {
    }

    public record PendingRow(UUID attemptId, String attemptNo, String instrumentTitle, String cohortName, String learnerName, UUID assessorId, String assessorName,
                             String decision, LocalDate decidedOn) {
    }
}
