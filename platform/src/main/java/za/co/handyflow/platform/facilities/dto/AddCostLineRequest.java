package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** costType: LABOUR, PARTS, TRAVEL, CONTRACTOR or OTHER. Total is computed as quantity x unitCost. */
public record AddCostLineRequest(
        @NotBlank String costType, @NotBlank String description,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitCost
) {}