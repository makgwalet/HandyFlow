package za.co.handyflow.platform.agriculture.dto;

import java.util.UUID;

public record UpdateGroupRequest(UUID productionAreaId, UUID enterpriseId, String breed, String notes) {}
