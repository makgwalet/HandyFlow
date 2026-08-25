package za.co.handyflow.platform.accountant.dto;

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
 * ⚠ The five other fields are confirmed via their real usage in
 * AccountantService.recordPayment() — this record's exact original
 * validation annotations weren't independently confirmed, only the
 * field names/types actually used.
 */
public record RecordPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paymentDate,
        @NotNull String paymentMethod,
        String reference,
        String notes,
        UUID bankAccountId          // NEW — nullable; see class Javadoc
) {}