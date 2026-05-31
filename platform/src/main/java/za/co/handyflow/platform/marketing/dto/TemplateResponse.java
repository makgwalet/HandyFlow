package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID    id,
        String  name,
        String  subject,
        String  htmlBody,
        String  previewText,
        String  category,
        Instant createdAt
) {}
