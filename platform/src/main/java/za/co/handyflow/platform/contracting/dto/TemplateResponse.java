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
        Instant createdAt,
        int templateVersion,
        UUID familyId,
        String status,              // DRAFT, INTERNAL_REVIEW, LEGAL_REVIEW, APPROVED, PUBLISHED, SUPERSEDED
        String legalStatus,         // NOT_REVIEWED, APPROVED, CONDITIONAL, REJECTED, EXPIRED, WITHDRAWN (for this version)
        String legalArea,
        String jurisdiction,
        String requiredSigningMethod,
        Instant publishedAt
) {}