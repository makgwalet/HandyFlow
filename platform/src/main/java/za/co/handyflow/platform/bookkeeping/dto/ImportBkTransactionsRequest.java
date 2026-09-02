package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Mirrors {@code accounting.ImportBankTransactionsRequest}'s own shape — base64-encoded CSV content. */
public record ImportBkTransactionsRequest(@NotNull UUID bankAccountId, @NotBlank String csvBase64) {}
