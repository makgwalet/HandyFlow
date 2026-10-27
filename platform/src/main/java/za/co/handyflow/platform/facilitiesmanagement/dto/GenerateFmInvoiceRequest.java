package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code basis} (optional): RETAINER bills a retainer's flat fee (name it with {@code agreementId} if the client
 * has several); WORK_ORDERS bills the CHARGEABLE completed work in the period (extras outside any retainer).
 * Left blank, a client with exactly one active retainer is billed that retainer and a client with none is billed
 * for chargeable work — as before. A client with several retainers must say which.
 */
public record GenerateFmInvoiceRequest(
        @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, UUID agreementId, String basis
) {}