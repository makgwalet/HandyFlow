package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — bankAccountId is new and deliberately optional.
 * Same gap and same fix already applied to every other module's
 * equivalent RecordPaymentRequest DTO this session. Needed to compile —
 * RecruitmentAgencyService.recordPayment() already calls
 * req.bankAccountId().
 * <p>
 * ⚠ The four other fields are confirmed via their real usage in
 * RecruitmentAgencyService (amount, paidDate, method, reference) — this
 * record's exact original validation annotations weren't independently
 * confirmed, only the field names/types actually used.
 */
public record RecordAgencyPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paidDate,
        @NotNull String method,
        String reference,
        UUID bankAccountId          // NEW — nullable; see class Javadoc
) {}