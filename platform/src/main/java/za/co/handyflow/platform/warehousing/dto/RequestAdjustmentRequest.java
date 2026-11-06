package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RequestAdjustmentRequest(@NotNull BigDecimal delta, @NotNull @Size(min = 5, max = 500) String reason) {}
