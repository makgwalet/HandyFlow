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
                  String  audienceType,    // ALL_OPTED_IN | SEGMENT | MANUAL
                  String  audienceFilter,  // JSON filter for SEGMENT
                  Instant scheduledAt,     // null = send immediately on launch
                  String  fromName,
                  String  replyTo
) {}
