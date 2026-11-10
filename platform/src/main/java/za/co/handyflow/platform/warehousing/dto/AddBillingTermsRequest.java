package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Billing terms from a start date. clientId null = the warehouse default. Any value left out carries forward from the terms
 * in force; at least one must be given.
 * <ul>
 *   <li>minimumStorageCharge: per 30 days;</li>
 *   <li>freeDays: stock received in the last N days (receipt day = day 1) is not charged per-unit storage;</li>
 *   <li>locationFeePerMonth: a flat fee for each pallet/bin position occupied, charged by the day.</li>
 * </ul>
 */
public record AddBillingTermsRequest(UUID clientId, @DecimalMin("0") BigDecimal minimumStorageCharge,
                                     @Min(0) @Max(3650) Integer freeDays, @DecimalMin("0") BigDecimal locationFeePerMonth,
                                     @NotNull LocalDate effectiveFrom, @Size(max = 300) String note) {}
