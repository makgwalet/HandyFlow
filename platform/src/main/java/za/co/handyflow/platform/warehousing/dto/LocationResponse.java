package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LocationResponse(
        UUID id, String code, String zone, String description, BigDecimal capacityUnits, boolean active
) {}
