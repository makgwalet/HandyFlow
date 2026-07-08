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
        String  status,
        Instant scheduledAt,
        Instant sentAt,
        int     recipientCount,
        int     sentCount,
        int     bouncedCount,
        int     unsubscribedCount,
        // NEW: the fields the frontend already expected — see MktCampaign's
        // own comment on why these didn't exist until now.
        int     openCount,
        int     clickCount,
        String  fromName,
        String  replyTo,
        Instant createdAt
) {}
