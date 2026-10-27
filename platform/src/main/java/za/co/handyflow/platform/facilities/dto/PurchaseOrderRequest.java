package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull UUID vendorId, LocalDate expectedDate, @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines
) {
    public record Line(@NotNull UUID itemId, @NotNull @DecimalMin("0.001") BigDecimal quantity, @NotNull @DecimalMin("0") BigDecimal unitCost) {}
}
