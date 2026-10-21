package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotNull;

public record CatalogueSettingsRequest(@NotNull Boolean priceApprovalRequired) {}
