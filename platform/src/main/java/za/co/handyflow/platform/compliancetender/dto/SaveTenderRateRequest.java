package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code active} is optional: left out, an update keeps the rate as it is. */
public record SaveTenderRateRequest(
        @NotBlank String category,
        @Size(max = 40) String itemRef,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 30) String unit,
        @NotNull @DecimalMin("0") @DecimalMax("9999999999999") BigDecimal unitCost,
        @Size(max = 120) String supplier,
        @Size(max = 500) String notes,
        Boolean active
) {}
