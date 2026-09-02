package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AdjustInventoryRequest(
        @NotNull BigDecimal newQuantity,
        UUID performedBy,
        String notes
) {}
