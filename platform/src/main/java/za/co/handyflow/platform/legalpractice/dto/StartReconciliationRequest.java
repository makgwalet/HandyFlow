package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code periodEnd} is the date the bank statement balance is as at. */
public record StartReconciliationRequest(@NotNull LocalDate periodEnd, @NotNull BigDecimal statementBalance, String note) {}
