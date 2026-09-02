package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FmAssetResponse(
        UUID id, UUID siteId, String assetTag, String name, String assetType, String location,
        String manufacturer, String model, String serialNumber, LocalDate installDate,
        LocalDate warrantyExpiryDate, String criticality, String status, String notes, Instant createdAt
) {}
