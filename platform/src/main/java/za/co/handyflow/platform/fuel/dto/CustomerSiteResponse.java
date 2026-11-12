package za.co.handyflow.platform.fuel.dto;

import java.util.UUID;

public record CustomerSiteResponse(UUID id, UUID accountId, String name, String address, String contactName,
                                   String contactPhone, String deliveryInstructions, String operatingHours,
                                   java.math.BigDecimal distanceKm, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {}
