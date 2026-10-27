package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Move stock of one item from one store to another. */
public record TransferStockRequest(
        @NotNull UUID fromStoreId, @NotNull UUID toStoreId, @NotNull @DecimalMin("0.001") BigDecimal quantity, @Size(max = 200) String reason
) {}
