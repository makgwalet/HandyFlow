package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response records for the campaign lifecycle and content studio. */
public final class StudioDtos {
    private StudioDtos() {}

    public record UpdateCampaignRequest(
            @NotBlank @Size(max = 255) String name,
            UUID templateId,
            @Size(max = 500) String subject,
            @Size(max = 600000) String htmlBody,
            String audienceType,          // ALL_OPTED_IN | SEGMENT
            UUID segmentId,
            @Size(max = 150) String fromName,
            @Size(max = 255) String replyTo,
            Instant scheduledAt,          // null = unscheduled (DRAFT)
            @Size(max = 300) String changeNote
    ) {}

    public record ReviewDecisionRequest(@Size(max = 1000) String note) {}

    public record CampaignVersionResponse(
            int version, String subject, String htmlBody, String fromName, String replyTo,
            String audienceType, UUID segmentId, String segmentName,
            String changeNote, String createdByName, Instant createdAt) {}

    public record TestSendRequest(List<String> emails) {}
    public record Refused(String email, String reason) {}
    public record TestSendResult(List<String> sent, List<Refused> refused) {}

    public record LintRequest(@Size(max = 1000) String subject, @Size(max = 600000) String htmlBody) {}
    /** level: ERROR | WARN | INFO */
    public record LintIssue(String level, String code, String message) {}

    public record ContentPreviewRequest(
            @Size(max = 1000) String subject, @Size(max = 600000) String htmlBody,
            @Size(max = 150) String sampleName, @Size(max = 255) String replyTo) {}
    public record ContentPreviewResponse(String subject, String html) {}

    public record BlockRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 50) String category,
            @NotBlank @Size(max = 50000) String html) {}
    public record BlockResponse(UUID id, String name, String category, String html, Instant updatedAt) {}

    public record MarketingSettings(boolean requireApproval, boolean allowSelfApproval) {}
}