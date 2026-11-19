package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentEvidenceResponse(
        UUID    id,
        String  eventType,
        String  channel,
        String  purpose,
        String  source,
        String  evidenceText,
        UUID    recordedBy,
        Instant createdAt
) {}