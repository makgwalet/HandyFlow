package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotBlank String  name,
        String  channel,         // EMAIL (default)
        UUID    templateId,
        String  subject,         // required if no templateId
        String  htmlBody,        // required if no templateId
        String  audienceType,    // ALL_OPTED_IN | SEGMENT
        String  audienceFilter,  // legacy, unused
        Instant scheduledAt,     // null = send immediately on launch
        String  fromName,
        String  replyTo,
        UUID    segmentId        // required when audienceType = SEGMENT
) {}