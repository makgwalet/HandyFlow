package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartInspectionRequest(@NotNull UUID templateId, @NotNull UUID siteId, UUID assetId) {}
