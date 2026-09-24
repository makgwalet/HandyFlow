package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientComplianceRequirementResponse(
        UUID id, UUID clientId, String code, String name, String appliesTo, String evidenceType,
        boolean required, int requirementVersion, Instant createdAt
) {}
