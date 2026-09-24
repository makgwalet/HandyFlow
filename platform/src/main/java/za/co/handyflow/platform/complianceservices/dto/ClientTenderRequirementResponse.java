package za.co.handyflow.platform.complianceservices.dto;

import java.util.UUID;

public record ClientTenderRequirementResponse(
        UUID id, UUID clientTenderId, UUID clientRequirementId, String description, String source, String status
) {}
