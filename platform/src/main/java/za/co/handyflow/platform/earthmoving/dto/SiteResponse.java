package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SiteResponse(UUID id, String name, String address, BigDecimal latitude, BigDecimal longitude,
                           UUID customerId, String customerName, UUID projectId, String projectName,
                           UUID supervisorUserId, String notes, boolean active) {}
