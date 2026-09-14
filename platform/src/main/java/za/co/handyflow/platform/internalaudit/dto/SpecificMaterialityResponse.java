package za.co.handyflow.platform.internalaudit.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SpecificMaterialityResponse(UUID id, String accountOrGlSegment, BigDecimal threshold) {}
