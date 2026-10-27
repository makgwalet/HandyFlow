package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One delivery. unitCost is the price actually invoiced; it defaults to the ordered price. */
public record ReceiveOrderRequest(
        @Size(max = 300) String reference, @NotEmpty @Size(max = 50) List<@Valid Line> lines
) {
    public record Line(@NotNull UUID lineId, @NotNull @DecimalMin("0.001") BigDecimal quantity, @DecimalMin("0") BigDecimal unitCost) {}
}
