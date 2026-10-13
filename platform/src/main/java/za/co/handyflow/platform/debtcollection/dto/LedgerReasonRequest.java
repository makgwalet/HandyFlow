package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record LedgerReasonRequest(@NotBlank String reason, LocalDate effectiveDate) {}
