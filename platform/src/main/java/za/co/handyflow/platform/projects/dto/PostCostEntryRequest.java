package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.projects.domain.enums.CostEntryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Manual ledger posting. amount is SIGNED and non-zero: a credit note or a release of a
 * commitment is a negative amount. sourceType defaults to MANUAL and sourceRef to a generated
 * value when omitted. Supply idempotencyKey to make a retried request harmless — the same key
 * with the same details returns the original entry instead of posting twice.
 */
public record PostCostEntryRequest(
        @NotNull(message = "Budget line is required") UUID budgetLineId,
        @NotNull(message = "Entry type is required (COMMITMENT or ACTUAL)") CostEntryType type,
        @NotNull(message = "Amount is required") BigDecimal amount,
        @Size(max = 30,  message = "Source type must not exceed 30 characters")  String sourceType,
        @Size(max = 100, message = "Source reference must not exceed 100 characters") String sourceRef,
        @Size(max = 500, message = "Description must not exceed 500 characters") String description,
        LocalDate entryDate,
        @Size(max = 120, message = "Idempotency key must not exceed 120 characters") String idempotencyKey
) {}