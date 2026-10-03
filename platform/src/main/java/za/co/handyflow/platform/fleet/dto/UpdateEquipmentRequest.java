package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;

/**
 * Sets both equipment numbers at once; a null clears that number. {@code operatingRatePerHour} is what an hour of use costs to keep the machine
 * running: service and repairs ONLY, never fuel (allocated from fuel dispatches) and never depreciation.
 */
public record UpdateEquipmentRequest(BigDecimal engineHours, BigDecimal operatingRatePerHour) {}
