package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** kind is OUTSTANDING_DEPOSIT or OUTSTANDING_PAYMENT. {@code trustTransactionId} links the ledger row it explains; the amount must then match it. */
public record AddReconItemRequest(@NotBlank String kind, @NotNull BigDecimal amount, @NotNull LocalDate itemDate, @NotBlank String description,
                                  String reference, UUID trustTransactionId) {}
