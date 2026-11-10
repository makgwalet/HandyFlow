package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManifestOrderRequest(@NotNull UUID orderId) {}
