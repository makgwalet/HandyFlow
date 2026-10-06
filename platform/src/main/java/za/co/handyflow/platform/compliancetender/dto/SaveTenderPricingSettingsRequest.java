package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Markups are percentages (0 to 100) on the basis described in TenderPriceCalculator. A null percentage means 0; a null vatApplies means VAT applies. */
public record SaveTenderPricingSettingsRequest(
        @DecimalMin("0") @DecimalMax("100") BigDecimal overheadPct,
        @DecimalMin("0") @DecimalMax("100") BigDecimal contingencyPct,
        @DecimalMin("0") @DecimalMax("100") BigDecimal profitPct,
        Boolean vatApplies,
        @Size(max = 5000) String notes
) {}
