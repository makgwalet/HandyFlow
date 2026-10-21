package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;

public record CatalogueSettingsResponse(boolean priceApprovalRequired, Instant updatedAt) {}
