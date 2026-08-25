package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — bankAccountId is new and deliberately optional.
 * Same gap and same fix already applied to invoicing/clinic's
 * equivalent RecordPaymentRequest DTOs.
 * <p>
 * ⚠ The four other fields (amount, paidDate, method, reference) are
 * confirmed via their real usage in BookingAgencyService.recordPayment()
 * — this record's exact original validation annotations weren't
 * independently confirmed, only the field names/types actually used.
 */
public record RecordBookAgencyPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paidDate,
        @NotNull String method,
        String reference,
        UUID bankAccountId          // NEW — nullable; see class Javadoc
) {}