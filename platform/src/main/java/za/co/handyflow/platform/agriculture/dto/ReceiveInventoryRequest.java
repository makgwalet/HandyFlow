package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiveInventoryRequest(
        @NotNull BigDecimal quantity,
        BigDecimal newUnitCost,
        UUID performedBy,
        String notes
) {}
