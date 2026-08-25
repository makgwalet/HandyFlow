package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — bankAccountId is new and deliberately optional.
 * Same gap and same fix already applied to invoicing/clinic/booking
 * agency's equivalent RecordPaymentRequest DTOs.
 * <p>
 * ⚠ The four other fields are confirmed via their real usage in
 * PayrollBureauService.recordPayment() — this record's exact original
 * validation annotations weren't independently confirmed, only the
 * field names/types actually used.
 */
public record RecordPayFeeNotePaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paidDate,
        @NotNull String method,
        String reference,
        UUID bankAccountId          // NEW — nullable; see class Javadoc
) {}