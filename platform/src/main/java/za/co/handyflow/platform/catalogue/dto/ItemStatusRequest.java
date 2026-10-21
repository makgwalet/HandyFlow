package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus;

public record ItemStatusRequest(@NotNull(message = "Choose a status") CatalogueItemStatus status, String reason, Long version) {}
