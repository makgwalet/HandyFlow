package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiveInventoryRequest(
        @NotNull BigDecimal quantity,
        BigDecimal newUnitCost,
        UUID performedBy,
        String notes,
        UUID supplierId   // optional: the Supply Chain supplier this was bought from; defaults to the item's usual supplier
) {}
