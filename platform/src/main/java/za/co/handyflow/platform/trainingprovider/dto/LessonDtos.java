package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LessonDtos {
    private LessonDtos() {
    }

    public record LessonRequest(@NotNull UUID programmeVersionId, UUID moduleId, @NotBlank String title, @NotBlank String kind, String contentText, String url, Integer position) {
    }

    public record LessonUpdate(UUID moduleId, @NotBlank String title, @NotBlank String kind, String contentText, String url, Integer position) {
    }

    public record ApproveRequest(String overrideReason) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record FinishRequest(@NotNull UUID enrolmentId, @NotNull UUID lessonId, @NotNull LocalDate finishedOn, String note) {
    }

    public record LessonView(UUID id, String lessonNo, UUID programmeVersionId, Integer versionNo, UUID moduleId, String moduleTitle, String title, String kind, String contentText,
                             String url, int position, String status, Instant approvedAt, String approvalOverrideReason, String retiredReason, List<String> nextStatuses) {
    }

    public record ProgressRow(UUID lessonId, String lessonNo, String title, String kind, UUID moduleId, int position, UUID progressId, LocalDate finishedOn, String note) {
    }

    public record ProgressView(UUID enrolmentId, String enrolmentNo, String learnerName, String programmeTitle, Integer versionNo, List<ProgressRow> rows, int finished, int total, String summary) {
    }

    public record ProgressRecord(UUID id, UUID enrolmentId, UUID lessonId, LocalDate finishedOn, String note, String status, String reversedReason) {
    }
}
