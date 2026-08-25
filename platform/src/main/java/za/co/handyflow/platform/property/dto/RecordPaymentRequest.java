package za.co.handyflow.platform.property.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — bankAccountId is new and deliberately optional.
 * Same gap and same fix already applied to every other module's
 * equivalent RecordPaymentRequest DTO this session.
 * <p>
 * ⚠ The four other fields are confirmed via their real usage in
 * PropertyService.recordPayment() — this record's exact original
 * validation annotations weren't independently confirmed, only the
 * field names/types actually used.
 */
public record RecordPaymentRequest(
        @NotNull @Positive BigDecimal amountPaid,
        @NotNull LocalDate paidDate,
        @NotNull String paymentMethod,
        String reference,
        UUID bankAccountId          // NEW — nullable; see class Javadoc
) {}