package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;

public record CatalogueSettingsResponse(boolean priceApprovalRequired, boolean itemApprovalRequired, Instant updatedAt) {
    public CatalogueSettingsResponse(boolean priceApprovalRequired, Instant updatedAt) { this(priceApprovalRequired, false, updatedAt); }
}
