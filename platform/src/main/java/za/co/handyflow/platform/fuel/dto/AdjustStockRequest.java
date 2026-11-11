package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Signed correction: positive adds fuel found, negative removes fuel lost. Reason is mandatory. */
public record AdjustStockRequest(
        @NotNull BigDecimal litres,
        @NotBlank @Size(min = 5, max = 500, message = "Reason must be 5 to 500 characters") String reason
) {}
