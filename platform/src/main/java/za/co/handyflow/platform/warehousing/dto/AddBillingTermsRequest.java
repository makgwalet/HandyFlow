package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Minimum storage charge per 30 days. clientId null = the warehouse default. */
public record AddBillingTermsRequest(UUID clientId, @NotNull @DecimalMin("0") BigDecimal minimumStorageCharge,
                                     @NotNull LocalDate effectiveFrom, @Size(max = 300) String note) {}
