package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A schedule line: quantity and unit cost are what the item costs the tenant, never the price quoted. A blank section becomes "General". */
public record SaveTenderPricingLineRequest(
        @Size(max = 120) String section,
        @Size(max = 40) String itemRef,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 30) String unit,
        @NotNull @DecimalMin("0") @DecimalMax("999999999999") BigDecimal quantity,
        @NotNull @DecimalMin("0") @DecimalMax("9999999999999") BigDecimal unitCost
) {}
