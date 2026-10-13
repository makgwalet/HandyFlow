package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * externalReference (bank reference / gateway transaction id) makes recording idempotent. allocateToFees overrides the
 * default allocation (principal first unless configured fees-first). ptpId also files the amount as a receipt on that promise.
 */
public record RecordPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        LocalDate effectiveDate,
        @NotNull PaymentSource source,
        @Size(max = 100) String externalReference,
        @PositiveOrZero BigDecimal allocateToFees,
        UUID ptpId,
        String notes
) {}
