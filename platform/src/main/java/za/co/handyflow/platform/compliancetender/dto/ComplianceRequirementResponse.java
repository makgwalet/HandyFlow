package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.util.UUID;

public record ComplianceRequirementResponse(
        UUID id, String code, String name, String appliesTo, String evidenceType,
        boolean required, int requirementVersion, Instant createdAt
) {}
