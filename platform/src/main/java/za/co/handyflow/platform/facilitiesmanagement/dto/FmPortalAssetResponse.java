package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.LocalDate;
import java.util.UUID;

/** What a client contact may see of an asset at one of their sites. EXCLUDES criticality, serial number and staff notes. */
public record FmPortalAssetResponse(
        UUID id, UUID siteId, String assetTag, String name, String assetType, String location,
        String manufacturer, String model, String status, LocalDate installDate, LocalDate warrantyExpiryDate
) {}
