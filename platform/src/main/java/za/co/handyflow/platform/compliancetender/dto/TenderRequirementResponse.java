package za.co.handyflow.platform.compliancetender.dto;

import java.util.UUID;

public record TenderRequirementResponse(
        UUID id, UUID tenderId, UUID complianceRequirementId, String description, String source, String status
) {}
