package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * costType: LABOUR, PARTS, TRAVEL, CONTRACTOR or OTHER. {@code unitCost} is what it costs the FM company;
 * {@code unitCharge} is what the client pays (optional — defaults to the cost, i.e. no markup).
 * {@code billable} defaults to true; set false for costs absorbed by the FM company (job-costing only, never invoiced).
 */
public record AddFmCostLineRequest(
        @NotBlank String costType, @NotBlank String description,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitCost,
        @DecimalMin("0.00") BigDecimal unitCharge,
        Boolean billable
) {}