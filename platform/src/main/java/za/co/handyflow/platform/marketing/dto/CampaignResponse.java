package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
        UUID    id,
        String  name,
        String  channel,
        UUID    templateId,
        String  templateName,
        String  subject,
        String  audienceType,
        UUID    segmentId,
        String  segmentName,
        String  status,
        Instant scheduledAt,
        Instant sentAt,
        int     recipientCount,
        int     sentCount,
        int     bouncedCount,
        int     unsubscribedCount,
        int     openCount,
        int     clickCount,
        int     suppressedCount,
        int     deliveredCount,
        int     complainedCount,
        int     failedCount,
        String  fromName,
        String  replyTo,
        String  pauseReason,      // also holds the reason for a FAILED campaign
        Instant createdAt,
        // ── Chunk 5 ──
        String  reviewStatus,     // NOT_SUBMITTED | IN_REVIEW | APPROVED | CHANGES_REQUESTED
        int     contentVersion,
        Integer approvedVersion,
        Integer sentVersion,
        String  submittedByName,
        Instant submittedAt,
        boolean submittedByMe,
        String  reviewedByName,
        String  reviewNote
        UUID    topicId,
        String  topicName
) {}