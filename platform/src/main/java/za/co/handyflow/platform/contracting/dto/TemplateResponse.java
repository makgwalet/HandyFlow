package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String contractType,
        String description,
        String bodyTemplate,
        Map<String, String> variables,
        boolean isSystem,
        Instant createdAt
) {}