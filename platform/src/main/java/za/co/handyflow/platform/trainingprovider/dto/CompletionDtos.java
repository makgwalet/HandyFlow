package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CompletionDtos {
    private CompletionDtos() {
    }

    public record RecordCompletion(@NotNull UUID enrolmentId, @NotBlank String outcome, @NotNull LocalDate outcomeOn, @NotBlank String basis, String overrideReason) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record ReadinessRow(UUID instrumentId, String instrumentNo, String instrumentTitle, boolean requiresModeration, String state, int attempts) {
    }

    public record Readiness(UUID enrolmentId, String enrolmentNo, String enrolmentStatus, String learnerName, String programmeTitle, Integer versionNo,
                            List<ReadinessRow> rows, int openGaps, String note) {
    }

    public record CompletionView(UUID id, String completionNo, UUID enrolmentId, String enrolmentNo, UUID learnerId, String learnerName, String programmeTitle,
                                 String outcome, LocalDate outcomeOn, String basis, int openGaps, String gapsSnapshot, String gapsOverrideReason, String status,
                                 String withdrawnReason, Instant recordedAt) {
    }
}
