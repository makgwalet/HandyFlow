package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AssetResponse(
        UUID id, UUID siteId, String assetTag, String name, String assetType, String location,
        String manufacturer, String model, String serialNumber, LocalDate installDate,
        LocalDate warrantyExpiryDate, String criticality, String status, String notes, Instant createdAt
) {}
