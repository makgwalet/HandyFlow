package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiveLineRequest(@NotNull @Positive BigDecimal qty, @NotNull UUID locationId) {}
