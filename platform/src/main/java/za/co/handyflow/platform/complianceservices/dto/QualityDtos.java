package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class QualityDtos {

    private QualityDtos() {}

    public record TemplateItemRequest(@NotBlank @Size(max = 500) String text, boolean critical) {}

    public record TemplateRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 12) String subjectType, @Size(max = 2000) String description,
                                  List<TemplateItemRequest> items) {}

    public record ActiveRequest(boolean active) {}

    public record TemplateLine(int position, String text, boolean critical) {}

    public record TemplateView(UUID id, String name, String subjectType, String description, boolean active, List<TemplateLine> items) {}

    public record StartRequest(@NotBlank @Size(max = 12) String subjectType, UUID subjectId, @Size(max = 250) String subjectLabel, @NotNull UUID templateId) {}

    public record MarkRequest(@NotBlank @Size(max = 8) String result, @Size(max = 2000) String note) {}

    public record CompleteRequest(@NotBlank @Size(max = 15) String outcome, @Size(max = 2000) String note, @Size(max = 1000) String exceptionReason) {}

    public record SampleRequest(@NotBlank @Size(max = 12) String subjectType, @NotNull LocalDate from, @NotNull LocalDate to, int ratePercent, @NotNull UUID templateId) {}

    public record SampleResult(int pool, int started) {}

    public record ItemView(int position, String text, boolean critical, String result, String note) {}

    public record ReviewSummary(UUID id, UUID clientId, String clientName, String subjectType, String subjectLabel, String templateName, int round, String status,
                                boolean sample, Instant startedAt, Instant completedAt, int checksDone, int checksTotal, int failed, List<String> actions) {}

    public record ReviewDetail(ReviewSummary review, UUID subjectId, UUID preparedBy, UUID reviewedBy, String outcomeNote, String exceptionReason, UUID previousReviewId,
                               List<ItemView> items) {}
}
