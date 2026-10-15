package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveSiteRequest(String name, String address, BigDecimal latitude, BigDecimal longitude,
                              UUID customerId, UUID projectId, UUID supervisorUserId, String notes) {}
