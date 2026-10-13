package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WaiveFeesRequest(@NotNull @Positive BigDecimal amount, LocalDate effectiveDate, @NotBlank String reason) {}
