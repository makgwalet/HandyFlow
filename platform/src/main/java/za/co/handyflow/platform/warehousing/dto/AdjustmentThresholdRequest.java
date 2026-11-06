package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Units above which an adjustment needs a second person. Null switches the approval rule off. */
public record AdjustmentThresholdRequest(@DecimalMin("0") BigDecimal thresholdQty, String reason) {}
