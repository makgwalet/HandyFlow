package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.marketing.dto.AnalyticsDtos.CampaignMetrics;
import za.co.handyflow.platform.marketing.dto.StudioDtos.LintIssue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JourneyDtos {
    private JourneyDtos() {}

    /** type: EMAIL | WAIT | EXIT_IF. Only the fields relevant to the type are used. */
    public record StepDto(String type, String name, String subject, String htmlBody, String fromName, String replyTo,
                          Integer amount, String unit /* HOURS|DAYS */, String condition /* CLICKED|OPENED */,
                          UUID campaignId /* set by the server at activation */) {}

    public record JourneyRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotBlank String triggerType,
            UUID segmentId,
            @NotNull @Size(min = 1, max = 20) List<@Valid StepDto> steps,
            Integer reentryDays, Integer minGapHours, Integer windowStartHour, Integer windowEndHour,
            Boolean skipWeekends) {}

    public record JourneySettingsRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            Integer minGapHours, Integer windowStartHour, Integer windowEndHour, Boolean skipWeekends) {}

    public record JourneyResponse(
            UUID id, String name, String description, String triggerType, UUID segmentId, String segmentName,
            String status, List<StepDto> steps, Integer reentryDays, int minGapHours, int windowStartHour,
            int windowEndHour, boolean skipWeekends, Instant activatedAt,
            long activeCount, long completedCount, long exitedCount, Instant createdAt, Instant updatedAt) {}

    public record EnrollmentRow(UUID id, UUID preferenceId, String email, String name, String status, int currentStep,
                                Instant nextRunAt, Instant enrolledAt, Instant completedAt, String exitReason, String lastError) {}

    public record LogEntry(int stepIndex, String stepType, String outcome, String detail, Instant at) {}

    public record StepStat(int index, String type, String summary, UUID campaignId, String campaignStatus,
                           String pauseReason, long waiting, CampaignMetrics metrics) {}

    public record JourneyStats(Map<String, Long> enrollments, List<StepStat> steps) {}

    public record ActivationPreview(long enrollNow, String note, List<LintIssue> issues) {}

    /** type: EMAIL | WAIT | EXIT_IF | BRANCH. yesTo/noTo (BRANCH only): index of a LATER step, -1 = end journey, null = next step. */
    public record StepDto(String type, String name, String subject, String htmlBody, String fromName, String replyTo,
                          Integer amount, String unit /* HOURS|DAYS */, String condition /* CLICKED|OPENED */,
                          UUID campaignId /* set by the server at activation */,
                          Integer yesTo, Integer noTo) {
        @JsonCreator public StepDto {}

        /** Existing call sites that don't use branching. */
        public StepDto(String type, String name, String subject, String htmlBody, String fromName, String replyTo,
                       Integer amount, String unit, String condition, UUID campaignId) {
            this(type, name, subject, htmlBody, fromName, replyTo, amount, unit, condition, campaignId, null, null);
        }
    }
}